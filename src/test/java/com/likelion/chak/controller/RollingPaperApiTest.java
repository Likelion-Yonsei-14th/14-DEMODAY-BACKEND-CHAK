package com.likelion.chak.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RollingPaperApiTest {

    private static final String BASE = "/api/public/rolling-papers";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    /** 한 사람(비회원 쿠키 하나)으로 요청을 보낸다. 처음 쓰는 요청에서 쿠키를 받는다. */
    private final class Visitor {
        private String guestKey;
        private final String ip = "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250)
                + "." + (int) (Math.random() * 250);

        MvcResult send(MockHttpServletRequestBuilder request) throws Exception {
            request.header("X-Forwarded-For", ip);
            if (guestKey != null) {
                request.cookie(new Cookie("chak_guest_id", guestKey));
            }
            MvcResult result = mockMvc.perform(request).andReturn();
            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            if (setCookie != null && setCookie.startsWith("chak_guest_id=")) {
                guestKey = setCookie.substring("chak_guest_id=".length(), setCookie.indexOf(';'));
            }
            return result;
        }

        JsonNode ok(MockHttpServletRequestBuilder request, int status) throws Exception {
            MvcResult result = send(request);
            assertThat(result.getResponse().getStatus())
                    .as(result.getResponse().getContentAsString())
                    .isEqualTo(status);
            String body = result.getResponse().getContentAsString();
            return body.isEmpty() ? null : mapper.readTree(body);
        }

        String error(MockHttpServletRequestBuilder request, int status) throws Exception {
            return ok(request, status).get("code").asString();
        }
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String note(String text, double x, double y) {
        return "{\"text\":\"" + text + "\",\"pen\":\"blue\",\"stamp\":\"heart\",\"x\":" + x + ",\"y\":" + y + "}";
    }

    private JsonNode createClass(Visitor creator, String revealMode) throws Exception {
        return creator.ok(json(post(BASE), "{\"name\":\"3학년 2반\",\"displayName\":\"민지\","
                + "\"paperDesign\":\"bg-stationery-note-pink\",\"revealMode\":\"" + revealMode + "\"}"), 201);
    }

    private JsonNode join(Visitor friend, String code, String name) throws Exception {
        return friend.ok(json(post(BASE + "/" + code + "/members"),
                "{\"displayName\":\"" + name + "\",\"paperDesign\":\"bg-basic-cream\"}"), 201);
    }

    @Test
    void friendsWriteOnEachOthersPapersAndTheOwnersPaperOpensWhenTheCreatorEndsIt() throws Exception {
        Visitor minji = new Visitor();
        JsonNode created = createClass(minji, "ON_END");
        String code = created.get("code").asString();
        long minjiId = created.get("me").get("memberId").asLong();
        assertThat(minji.guestKey).isNotNull();
        assertThat(created.get("me").get("creator").asBoolean()).isTrue();
        assertThat(created.get("members")).hasSize(1);

        // 링크로 처음 온 친구에게는 이름과 인원수만 보인다.
        Visitor hyunwoo = new Visitor();
        JsonNode preview = hyunwoo.ok(get(BASE + "/" + code), 200);
        assertThat(preview.get("name").asString()).isEqualTo("3학년 2반");
        assertThat(preview.get("memberCount").asLong()).isEqualTo(1);
        assertThat(preview.get("me").isNull()).isTrue();
        assertThat(preview.get("members")).isEmpty();
        assertThat(hyunwoo.error(get(BASE + "/" + code + "/papers/" + minjiId), 403)).isEqualTo("ROLLING_PAPER_NOT_MEMBER");

        JsonNode joined = join(hyunwoo, code, "현우");
        long hyunwooId = joined.get("me").get("memberId").asLong();
        assertThat(joined.get("members")).hasSize(2);
        assertThat(hyunwoo.error(json(post(BASE + "/" + code + "/members"),
                "{\"displayName\":\"또현우\",\"paperDesign\":\"bg-basic-cream\"}"), 409)).isEqualTo("ROLLING_PAPER_ALREADY_JOINED");

        // 현우가 민지 종이에 쓴다. 다시 쓰면 같은 글이 고쳐지고 자리를 옮긴다.
        hyunwoo.ok(json(put(BASE + "/" + code + "/papers/" + minjiId + "/note"), note("졸업 축하해", 30, 20)), 200);
        JsonNode moved = hyunwoo.ok(json(put(BASE + "/" + code + "/papers/" + minjiId + "/note"), note("고쳐 쓴 말", 70.456, 80)), 200);
        assertThat(moved.get("notes")).hasSize(1);
        assertThat(moved.get("notes").get(0).get("text").asString()).isEqualTo("고쳐 쓴 말");
        assertThat(moved.get("notes").get(0).get("x").decimalValue()).isEqualByComparingTo("70.46");
        assertThat(moved.get("notes").get(0).get("mine").asBoolean()).isTrue();
        assertThat(moved.get("member").get("writtenByMe").asBoolean()).isTrue();

        // 자기 종이에는 못 쓰고, 종료 전 자기 종이는 개수만 보인다.
        assertThat(minji.error(json(put(BASE + "/" + code + "/papers/" + minjiId + "/note"), note("셀프", 50, 50)), 400))
                .isEqualTo("ROLLING_PAPER_OWN_PAPER");
        JsonNode sealed = minji.ok(get(BASE + "/" + code + "/papers/" + minjiId), 200);
        assertThat(sealed.get("sealed").asBoolean()).isTrue();
        assertThat(sealed.get("noteCount").asLong()).isEqualTo(1);
        assertThat(sealed.get("notes")).isEmpty();
        JsonNode minjiView = minji.ok(get(BASE + "/" + code), 200);
        assertThat(minjiView.get("myPaper").get("open").asBoolean()).isFalse();
        assertThat(minjiView.get("myPaper").get("noteCount").asLong()).isEqualTo(1);

        // 반 친구는 친구 종이를 읽는다.
        Visitor seoyeon = new Visitor();
        join(seoyeon, code, "서연");
        JsonNode friendView = seoyeon.ok(get(BASE + "/" + code + "/papers/" + minjiId), 200);
        assertThat(friendView.get("sealed").asBoolean()).isFalse();
        assertThat(friendView.get("notes").get(0).get("authorName").asString()).isEqualTo("현우");
        assertThat(friendView.get("notes").get(0).get("mine").asBoolean()).isFalse();

        // 종료는 만든 사람만. 종료하면 자기 종이가 열리고 더는 쓸 수 없다.
        assertThat(hyunwoo.error(post(BASE + "/" + code + "/end"), 403)).isEqualTo("ROLLING_PAPER_CREATOR_ONLY");
        JsonNode ended = minji.ok(post(BASE + "/" + code + "/end"), 200);
        assertThat(ended.get("endedAt").isNull()).isFalse();
        assertThat(ended.get("myPaper").get("open").asBoolean()).isTrue();
        JsonNode opened = minji.ok(get(BASE + "/" + code + "/papers/" + minjiId), 200);
        assertThat(opened.get("sealed").asBoolean()).isFalse();
        assertThat(opened.get("notes")).hasSize(1);
        assertThat(seoyeon.error(json(put(BASE + "/" + code + "/papers/" + hyunwooId + "/note"), note("늦었다", 50, 50)), 409))
                .isEqualTo("ROLLING_PAPER_ENDED");
    }

    @Test
    void personalPaperHidesOtherWritersNotesFromEveryoneButTheOwner() throws Exception {
        Visitor minji = new Visitor();
        JsonNode created = minji.ok(json(post(BASE), "{\"name\":\"민지에게 한마디\",\"displayName\":\"민지\","
                + "\"paperDesign\":\"bg-stationery-note-pink\",\"revealMode\":\"INSTANT\",\"kind\":\"PERSONAL\"}"), 201);
        String code = created.get("code").asString();
        long minjiId = created.get("me").get("memberId").asLong();
        assertThat(created.get("kind").asString()).isEqualTo("PERSONAL");

        // 링크로 처음 온 사람에게는 누구의 종이인지(주인 이름·디자인)가 보인다.
        Visitor hyunwoo = new Visitor();
        JsonNode preview = hyunwoo.ok(get(BASE + "/" + code), 200);
        assertThat(preview.get("members")).hasSize(1);
        assertThat(preview.get("members").get(0).get("displayName").asString()).isEqualTo("민지");
        assertThat(preview.get("members").get(0).get("paperDesign").asString()).isEqualTo("bg-stationery-note-pink");

        // 쓰러 온 사람은 이름만 정하고(디자인 없이) 들어온다. 종이 목록에는 주인 것만 있다.
        JsonNode joined = hyunwoo.ok(json(post(BASE + "/" + code + "/members"), "{\"displayName\":\"현우\"}"), 201);
        long hyunwooId = joined.get("me").get("memberId").asLong();
        assertThat(joined.get("members")).hasSize(1);
        hyunwoo.ok(json(put(BASE + "/" + code + "/papers/" + minjiId + "/note"), note("올해 고마웠어", 30, 20)), 200);
        // 쓰러 온 사람에게는 종이가 없고, 칠판도 없다.
        assertThat(hyunwoo.error(get(BASE + "/" + code + "/papers/" + hyunwooId), 404)).isEqualTo("ROLLING_PAPER_MEMBER_NOT_FOUND");
        assertThat(minji.error(json(put(BASE + "/" + code + "/papers/" + hyunwooId + "/note"), note("나도", 50, 50)), 404))
                .isEqualTo("ROLLING_PAPER_MEMBER_NOT_FOUND");
        assertThat(hyunwoo.error(json(post(BASE + "/" + code + "/board"), "{\"text\":\"안녕\"}"), 400)).isEqualTo("INVALID_REQUEST");

        // 다음 사람은 현우 글의 자리만 받고, 글·이름은 받지 않는다(화면은 흐리게). 자기 글은 그대로 본다.
        Visitor seoyeon = new Visitor();
        seoyeon.ok(json(post(BASE + "/" + code + "/members"), "{\"displayName\":\"서연\"}"), 201);
        JsonNode written = seoyeon.ok(json(put(BASE + "/" + code + "/papers/" + minjiId + "/note"), note("메리 크리스마스", 60, 70)), 200);
        assertThat(written.get("notes")).hasSize(2);
        JsonNode others = written.get("notes").get(0);
        assertThat(others.get("hidden").asBoolean()).isTrue();
        assertThat(others.get("text").isNull()).isTrue();
        assertThat(others.get("authorName").asString()).isEmpty();
        assertThat(others.get("authorMemberId").isNull()).isTrue();
        assertThat(others.get("x").decimalValue()).isEqualByComparingTo("30");
        JsonNode own = written.get("notes").get(1);
        assertThat(own.get("hidden").asBoolean()).isFalse();
        assertThat(own.get("text").asString()).isEqualTo("메리 크리스마스");

        // 주인은 모두 읽는다.
        JsonNode ownerView = minji.ok(get(BASE + "/" + code + "/papers/" + minjiId), 200);
        assertThat(ownerView.get("notes")).hasSize(2);
        assertThat(ownerView.get("notes").get(0).get("text").asString()).isEqualTo("올해 고마웠어");
        assertThat(ownerView.get("notes").get(0).get("authorName").asString()).isEqualTo("현우");
        assertThat(ownerView.get("notes").get(0).get("hidden").asBoolean()).isFalse();

        JsonNode mine = hyunwoo.ok(get(BASE + "/mine"), 200);
        assertThat(mine.get(0).get("kind").asString()).isEqualTo("PERSONAL");
    }

    @Test
    void instantRevealShowsTheOwnersPaperRightAway() throws Exception {
        Visitor minji = new Visitor();
        JsonNode created = createClass(minji, "INSTANT");
        String code = created.get("code").asString();
        long minjiId = created.get("me").get("memberId").asLong();
        Visitor hyunwoo = new Visitor();
        join(hyunwoo, code, "현우");
        hyunwoo.ok(json(put(BASE + "/" + code + "/papers/" + minjiId + "/note"), note("바로 보여", 40, 30)), 200);

        JsonNode mine = minji.ok(get(BASE + "/" + code + "/papers/" + minjiId), 200);
        assertThat(mine.get("sealed").asBoolean()).isFalse();
        assertThat(mine.get("notes").get(0).get("text").asString()).isEqualTo("바로 보여");

        // 만든 사람이 공개 방식을 바꾸면 다시 닫힌다. 다른 사람은 바꿀 수 없다.
        assertThat(hyunwoo.error(json(patch(BASE + "/" + code), "{\"revealMode\":\"ON_END\"}"), 403))
                .isEqualTo("ROLLING_PAPER_CREATOR_ONLY");
        minji.ok(json(patch(BASE + "/" + code), "{\"name\":\"3학년 2반 졸업\",\"revealMode\":\"ON_END\"}"), 200);
        JsonNode resealed = minji.ok(get(BASE + "/" + code + "/papers/" + minjiId), 200);
        assertThat(resealed.get("sealed").asBoolean()).isTrue();
        assertThat(minji.ok(get(BASE + "/" + code), 200).get("name").asString()).isEqualTo("3학년 2반 졸업");
    }

    @Test
    void anyoneChalksOnTheBoardButWipesOnlyTheirOwnLines() throws Exception {
        Visitor minji = new Visitor();
        String code = createClass(minji, "ON_END").get("code").asString();
        Visitor hyunwoo = new Visitor();
        join(hyunwoo, code, "현우");

        JsonNode afterMinji = minji.ok(json(post(BASE + "/" + code + "/board"), "{\"text\":\"3년 동안 고생했다\"}"), 201);
        long minjiLine = afterMinji.get("board").get(0).get("id").asLong();
        JsonNode afterHyunwoo = hyunwoo.ok(json(post(BASE + "/" + code + "/board"), "{\"text\":\"졸업 축하해!\"}"), 201);
        assertThat(afterHyunwoo.get("board")).hasSize(2);
        assertThat(afterHyunwoo.get("board").get(0).get("authorName").asString()).isEqualTo("민지");
        assertThat(afterHyunwoo.get("board").get(0).get("mine").asBoolean()).isFalse();
        assertThat(afterHyunwoo.get("board").get(1).get("mine").asBoolean()).isTrue();

        assertThat(hyunwoo.error(delete(BASE + "/" + code + "/board/" + minjiLine), 403)).isEqualTo("FORBIDDEN");
        JsonNode wiped = minji.ok(delete(BASE + "/" + code + "/board/" + minjiLine), 200);
        assertThat(wiped.get("board")).hasSize(1);

        // 반에 없는 사람은 칠판에 못 쓴다.
        Visitor stranger = new Visitor();
        stranger.send(post(BASE + "/" + code + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\",\"paperDesign\":\"bg-basic-cream\"}"));
        assertThat(stranger.error(json(post(BASE + "/" + code + "/board"), "{\"text\":\"안녕\"}"), 403))
                .isEqualTo("ROLLING_PAPER_NOT_MEMBER");
    }

    @Test
    void rejectsUnknownPensDesignsAndPositionsOffThePaper() throws Exception {
        Visitor minji = new Visitor();
        JsonNode created = createClass(minji, "ON_END");
        String code = created.get("code").asString();
        long minjiId = created.get("me").get("memberId").asLong();
        Visitor hyunwoo = new Visitor();
        join(hyunwoo, code, "현우");
        String path = BASE + "/" + code + "/papers/" + minjiId + "/note";

        assertThat(hyunwoo.error(json(put(path),
                "{\"text\":\"안녕\",\"pen\":\"gold\",\"stamp\":\"heart\",\"x\":10,\"y\":10}"), 400)).isEqualTo("INVALID_REQUEST");
        assertThat(hyunwoo.error(json(put(path), note("안녕", 120, 10)), 400)).isEqualTo("INVALID_REQUEST");
        assertThat(hyunwoo.error(json(put(path), note("   ", 10, 10)), 400)).isEqualTo("INVALID_REQUEST");
        assertThat(hyunwoo.error(json(patch(BASE + "/" + code + "/members/me"),
                "{\"paperDesign\":\"https://evil.example/x.png\"}"), 400)).isEqualTo("INVALID_REQUEST");
        assertThat(hyunwoo.error(get(BASE + "/no-such-class"), 404)).isEqualTo("ROLLING_PAPER_NOT_FOUND");
    }

    @Test
    void membersChangeTheirOwnNameAndDesignAndOnlyTheCreatorDeletesTheClass() throws Exception {
        Visitor minji = new Visitor();
        JsonNode created = createClass(minji, "ON_END");
        String code = created.get("code").asString();
        long minjiId = created.get("me").get("memberId").asLong();
        Visitor hyunwoo = new Visitor();
        join(hyunwoo, code, "현우");
        hyunwoo.ok(json(put(BASE + "/" + code + "/papers/" + minjiId + "/note"), note("안녕", 30, 30)), 200);

        JsonNode renamed = hyunwoo.ok(json(patch(BASE + "/" + code + "/members/me"),
                "{\"displayName\":\"현우짱\",\"paperDesign\":\"bg-sky\"}"), 200);
        assertThat(renamed.get("me").get("displayName").asString()).isEqualTo("현우짱");
        assertThat(renamed.get("me").get("paperDesign").asString()).isEqualTo("bg-sky");

        // 내가 들어간 반 목록
        JsonNode mine = hyunwoo.ok(get(BASE + "/mine"), 200);
        assertThat(mine).anySatisfy(item -> assertThat(item.get("code").asString()).isEqualTo(code));

        assertThat(hyunwoo.error(delete(BASE + "/" + code), 403)).isEqualTo("ROLLING_PAPER_CREATOR_ONLY");
        minji.ok(delete(BASE + "/" + code), 200);
        assertThat(minji.error(get(BASE + "/" + code), 404)).isEqualTo("ROLLING_PAPER_NOT_FOUND");
        assertThat(hyunwoo.ok(get(BASE + "/mine"), 200))
                .noneSatisfy(item -> assertThat(item.get("code").asString()).isEqualTo(code));
    }
}
