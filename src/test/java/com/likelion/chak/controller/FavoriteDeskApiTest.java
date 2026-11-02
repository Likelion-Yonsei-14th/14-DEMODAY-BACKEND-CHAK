package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.repository.FavoriteDeskRepository;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.AdminUserService;
import com.likelion.chak.service.DeskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteDeskApiTest {

    private static final String PATH = "/api/users/me/favorite-desks";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private PersonalDeskRepository deskRepository;
    @Autowired private FavoriteDeskRepository favoriteRepository;
    @Autowired private DeskService deskService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private AlertNotifier notifier;

    private UserAccount user(String name) {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
    }

    private String auth(UserAccount user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    /** 새 회원과 그 사람의 책상을 만들고 책상 링크 토큰을 돌려준다. */
    private String deskOf(String ownerName, String deskName) {
        UserAccount owner = user(ownerName);
        deskService.createMyDesk(owner, new DeskCreateRequest(
                deskName, ReadModeType.TIME_CAPSULE, null, Instant.parse("2099-01-01T00:00:00Z"), true, MessageVisibility.PUBLIC));
        return deskRepository.findByOwnerId(owner.getId()).orElseThrow().getSupporterToken();
    }

    private JsonNode list(UserAccount me) throws Exception {
        return mapper.readTree(mockMvc.perform(get(PATH).header("Authorization", auth(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void aSignedInUserStarsADeskAndSeesItInTheList() throws Exception {
        UserAccount me = user("조민준");
        String token = deskOf("지수", "지수의 책상");

        mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supporterToken").value(token))
                .andExpect(jsonPath("$.displayName").value("지수의 책상"))
                .andExpect(jsonPath("$.createdAt").exists());

        JsonNode favorites = list(me);
        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0).get("supporterToken").asString()).isEqualTo(token);
        assertThat(favorites.get(0).get("displayName").asString()).isEqualTo("지수의 책상");
    }

    @Test
    void anEmptyListIsAnEmptyArray() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", auth(user("새사람"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void starringTheSameDeskTwiceKeepsOneRowAndTheFirstTime() throws Exception {
        UserAccount me = user("두번");
        String token = deskOf("친구", "친구 책상");

        String first = mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(me)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(mapper.readTree(second).get("createdAt")).isEqualTo(mapper.readTree(first).get("createdAt"));
        assertThat(list(me)).hasSize(1);
    }

    @Test
    void theNewestStarComesFirst() throws Exception {
        UserAccount me = user("순서");
        String older = deskOf("가", "먼저 담은 책상");
        String newer = deskOf("나", "나중에 담은 책상");

        mockMvc.perform(put(PATH + "/" + older).header("Authorization", auth(me))).andExpect(status().isOk());
        Thread.sleep(15);
        mockMvc.perform(put(PATH + "/" + newer).header("Authorization", auth(me))).andExpect(status().isOk());

        JsonNode favorites = list(me);
        assertThat(favorites.get(0).get("displayName").asString()).isEqualTo("나중에 담은 책상");
        assertThat(favorites.get(1).get("displayName").asString()).isEqualTo("먼저 담은 책상");
    }

    @Test
    void eachUserHasTheirOwnList() throws Exception {
        UserAccount a = user("에이");
        UserAccount b = user("비");
        String token = deskOf("주인", "공용 책상");

        mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(a))).andExpect(status().isOk());

        assertThat(list(a)).hasSize(1);
        assertThat(list(b)).isEmpty();
        // 다른 사람이 풀어도 내 즐겨찾기는 그대로다.
        mockMvc.perform(delete(PATH + "/" + token).header("Authorization", auth(b))).andExpect(status().isNoContent());
        assertThat(list(a)).hasSize(1);
    }

    @Test
    void unstarringRemovesItAndDoingItAgainIsFine() throws Exception {
        UserAccount me = user("풀기");
        String token = deskOf("친구", "친구 책상");
        mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(me))).andExpect(status().isOk());

        mockMvc.perform(delete(PATH + "/" + token).header("Authorization", auth(me))).andExpect(status().isNoContent());
        mockMvc.perform(delete(PATH + "/" + token).header("Authorization", auth(me))).andExpect(status().isNoContent());

        assertThat(list(me)).isEmpty();
    }

    @Test
    void unstarringADeskThatNeverExistedIsNotAnError() throws Exception {
        mockMvc.perform(delete(PATH + "/" + UUID.randomUUID()).header("Authorization", auth(user("없는"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void starringAMissingDeskIsNotFound() throws Exception {
        mockMvc.perform(put(PATH + "/" + UUID.randomUUID()).header("Authorization", auth(user("못찾음"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DESK_NOT_FOUND"));
    }

    @Test
    void aDeletedDeskDisappearsFromTheListAndCannotBeStarredButCanStillBeUnstarred() throws Exception {
        UserAccount me = user("지운뒤");
        String token = deskOf("주인", "곧 지울 책상");
        mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(me))).andExpect(status().isOk());

        PersonalDesk desk = deskRepository.findBySupporterToken(token).orElseThrow();
        desk.softDelete(Instant.now());
        deskRepository.save(desk);

        assertThat(list(me)).isEmpty();
        mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(user("다른사람")))).andExpect(status().isNotFound());
        mockMvc.perform(delete(PATH + "/" + token).header("Authorization", auth(me))).andExpect(status().isNoContent());
        assertThat(favoriteRepository.findAll().stream().filter(f -> f.getUser().getId().equals(me.getId()))).isEmpty();
    }

    @Test
    void theListShowsTheDesksCurrentName() throws Exception {
        UserAccount me = user("이름");
        UserAccount owner = user("주인");
        deskService.createMyDesk(owner, new DeskCreateRequest(
                "예전 이름", ReadModeType.TIME_CAPSULE, null, Instant.parse("2099-01-01T00:00:00Z"), true, MessageVisibility.PUBLIC));
        String token = deskRepository.findByOwnerId(owner.getId()).orElseThrow().getSupporterToken();
        mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(me))).andExpect(status().isOk());

        PersonalDesk desk = deskRepository.findByOwnerId(owner.getId()).orElseThrow();
        desk.updateSettings("새 이름", ReadModeType.TIME_CAPSULE, null, Instant.parse("2099-01-01T00:00:00Z"), true, MessageVisibility.PUBLIC);
        deskRepository.save(desk);

        assertThat(list(me).get(0).get("displayName").asString()).isEqualTo("새 이름");
    }

    @Test
    void aGuestWithoutLoginCannotUseFavorites() throws Exception {
        String token = deskOf("주인", "책상");

        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH + "/" + token)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH + "/" + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void aDeskCanHoldAtMostOneHundredFavoritesPerUserButTheLimitDoesNotBlockWhatIsAlreadyThere() throws Exception {
        UserAccount me = user("백개");
        String firstToken = null;
        for (int index = 0; index < 100; index += 1) {
            String token = deskOf("주인" + index, "책상 " + index);
            if (firstToken == null) firstToken = token;
            mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(me))).andExpect(status().isOk());
        }
        String overflow = deskOf("마지막", "백한 번째");

        mockMvc.perform(put(PATH + "/" + overflow).header("Authorization", auth(me)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE_LIMIT_EXCEEDED"));
        // 이미 담은 책상을 다시 담는 건 한도와 상관없이 성공한다.
        mockMvc.perform(put(PATH + "/" + firstToken).header("Authorization", auth(me))).andExpect(status().isOk());
        // 하나를 풀면 다시 담을 수 있다.
        mockMvc.perform(delete(PATH + "/" + firstToken).header("Authorization", auth(me))).andExpect(status().isNoContent());
        mockMvc.perform(put(PATH + "/" + overflow).header("Authorization", auth(me))).andExpect(status().isOk());
        assertThat(list(me)).hasSize(100);
    }

    @Test
    void aWithdrawnUsersFavoritesAreRemoved() throws Exception {
        UserAccount admin = user("관리자");
        admin.updateAdmin(true);
        userRepository.save(admin);
        UserAccount leaving = user("탈퇴");
        String token = deskOf("주인", "책상");
        mockMvc.perform(put(PATH + "/" + token).header("Authorization", auth(leaving))).andExpect(status().isOk());
        assertThat(favoriteRepository.findAllLiveByUserId(leaving.getId())).hasSize(1);

        adminUserService.withdraw(admin.getId(), leaving.getId(), true);

        assertThat(favoriteRepository.findAllLiveByUserId(leaving.getId())).isEmpty();
    }
}
