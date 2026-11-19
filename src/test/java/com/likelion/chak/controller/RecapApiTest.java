package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.MinigameRound;
import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.repository.MinigameRoundRepository;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.service.DeskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecapApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DeskService deskService;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private MinigameRoundRepository roundRepository;
    @Autowired private JwtTokenProvider jwt;

    private UserAccount user(String name) {
        return userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), name, null, null));
    }

    private PersonalDesk desk(UserAccount owner) {
        deskService.createMyDesk(owner, new DeskCreateRequest(
                "책상", ReadModeType.TIME_CAPSULE, null, Instant.parse("2026-01-01T00:00:00Z"), true, MessageVisibility.PUBLIC));
        return deskService.findByOwnerId(owner.getId());
    }

    private void send(PersonalDesk desk, UserAccount author, String kind) throws Exception {
        String body = "{\"nickname\":\"친구\",\"kind\":\"" + kind + "\",\"visibility\":\"PUBLIC\",\"schemaVersion\":1,"
                + "\"card\":{\"pages\":[{\"id\":\"p1\",\"note\":\"힘내\"}]},"
                + "\"object\":{\"representationType\":\"" + ("STICKER".equals(kind) ? "STICKER" : "LETTER")
                + "\",\"x\":50,\"y\":50,\"rotation\":0,\"scale\":1,\"zIndex\":1}}";
        mockMvc.perform(post("/api/public/desks/" + desk.getSupporterToken() + "/messages")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(author))
                        .header("X-Forwarded-For", "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private void play(UserAccount user, MinigameType game, int score) {
        MinigameRound round = roundRepository.save(new MinigameRound(game, ""));
        round.submit(user, score, Instant.now());
        roundRepository.save(round);
    }

    @Test
    void countsReceivedSentAndGamesWithoutNames() throws Exception {
        UserAccount owner = user("주인");
        UserAccount friendA = user("친구A");
        UserAccount friendB = user("친구B");
        PersonalDesk mine = desk(owner);
        PersonalDesk friendDesk = desk(friendA);

        send(mine, friendA, "CARD");
        send(mine, friendA, "CARD");
        send(mine, friendB, "CARD");
        send(friendDesk, owner, "CARD");

        play(owner, MinigameType.DESK_BLAST, 900_000);
        play(owner, MinigameType.DESK_BLAST, 10);
        play(friendB, MinigameType.DESK_BLAST, 5);

        mockMvc.perform(get("/api/public/recap").header("Authorization", "Bearer " + jwt.createAccessToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member").value(true))
                .andExpect(jsonPath("$.received.letters").value(3))
                .andExpect(jsonPath("$.received.senders").value(2))
                .andExpect(jsonPath("$.received.peakCount").value(3))
                .andExpect(jsonPath("$.sent.letters").value(1))
                .andExpect(jsonPath("$.sent.desks").value(1))
                .andExpect(jsonPath("$.games", hasSize(1)))
                .andExpect(jsonPath("$.games[0].game").value("DESK_BLAST"))
                .andExpect(jsonPath("$.games[0].plays").value(2))
                .andExpect(jsonPath("$.games[0].best").value(900_000))
                .andExpect(jsonPath("$.games[0].topPercent").value(org.hamcrest.Matchers.lessThanOrEqualTo(50)));
    }

    @Test
    void someoneWithNothingStillGetsARecap() throws Exception {
        UserAccount quiet = user("조용");
        mockMvc.perform(get("/api/public/recap").header("Authorization", "Bearer " + jwt.createAccessToken(quiet)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(nullValue()))
                .andExpect(jsonPath("$.sent.letters").value(0))
                .andExpect(jsonPath("$.games", hasSize(0)));

        mockMvc.perform(get("/api/public/recap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member").value(false))
                .andExpect(jsonPath("$.sent.letters").value(0));
    }
}
