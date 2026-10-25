package com.likelion.chak.controller;

import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.UserAccountRepository;
import com.likelion.chak.repository.UserAvatarRepository;
import com.likelion.chak.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private UserAvatarRepository avatarRepository;
    @Autowired private AdminUserService adminUserService;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private ObjectMapper mapper;

    private UserAccount user(String name) {
        return userRepository.save(UserAccount.createGoogle("g-" + UUID.randomUUID(), name, "u@example.com", "https://lh3.example/photo.jpg"));
    }

    private String auth(UserAccount user) {
        return "Bearer " + jwt.createAccessToken(user);
    }

    private MockHttpServletRequestBuilder rename(UserAccount user, String name) {
        return patch("/api/users/me").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + name + "\"}");
    }

    private static byte[] jpeg(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 7);
        bytes[0] = (byte) 0xFF; bytes[1] = (byte) 0xD8; bytes[2] = (byte) 0xFF; bytes[3] = (byte) 0xE0;
        return bytes;
    }

    private static String dataUrl(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private MockHttpServletRequestBuilder upload(UserAccount user, String image) {
        return put("/api/users/me/avatar").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"image\":\"" + image + "\"}");
    }

    @Test
    void profileEditingNeedsALogin() throws Exception {
        mockMvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"가나다\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/me/avatar").contentType(MediaType.APPLICATION_JSON).content("{\"image\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/me/avatar")).andExpect(status().isUnauthorized());
    }

    @Test
    void renamingTrimsAndSticksAndShowsUpInMyInfo() throws Exception {
        UserAccount me = user("구글이름");
        mockMvc.perform(rename(me, "  새 이름  ")).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("새 이름"));
        mockMvc.perform(get("/api/users/me").header("Authorization", auth(me)))
                .andExpect(jsonPath("$.displayName").value("새 이름")).andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void badNamesAreRejected() throws Exception {
        UserAccount me = user("구글이름");
        mockMvc.perform(rename(me, "   ")).andExpect(status().isBadRequest());                 // 빈 이름은 형식 검사에서 먼저 걸린다
        for (String bad : new String[]{"스무글자를한참넘기는아주아주아주긴이름입니다람쥐", "<b>x</b>", "a@b.c"}) {
            mockMvc.perform(rename(me, bad)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DISPLAY_NAME"));
        }
        mockMvc.perform(rename(me, "시1발")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NICKNAME_NOT_ALLOWED"));
        mockMvc.perform(get("/api/users/me").header("Authorization", auth(me))).andExpect(jsonPath("$.displayName").value("구글이름"));
    }

    @Test
    void aNameTheMemberChoseSurvivesTheNextProviderLogin() {
        UserAccount me = user("구글이름");
        me.editDisplayName("내가 정한 이름");
        me.updateProfile("구글에서 바뀐 이름", "new@example.com", "https://lh3.example/new.jpg");

        assertThat(me.getDisplayName()).isEqualTo("내가 정한 이름");
        assertThat(me.getEmail()).isEqualTo("new@example.com");                 // 이메일·사진은 계속 제공자를 따른다
        assertThat(user("다른 사람").isProfileCustomized()).isFalse();

        UserAccount untouched = user("원래 이름");
        untouched.updateProfile("바뀐 이름", null, null);
        assertThat(untouched.getDisplayName()).isEqualTo("바뀐 이름");
    }

    @Test
    void uploadingAReplacingAndRemovingAPhotoWorksEndToEnd() throws Exception {
        UserAccount me = user("사진 주인");
        byte[] first = jpeg(2_000);
        String firstUrl = mapper.readTree(mockMvc.perform(upload(me, dataUrl("image/jpeg", first))).andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").exists()).andReturn().getResponse().getContentAsString()).get("avatarUrl").asString();
        assertThat(firstUrl).startsWith("/api/public/avatars/");

        // 로그인 없이 이미지 태그로 읽을 수 있고, 오래 캐시해도 되는 주소다.
        byte[] served = mockMvc.perform(get(firstUrl)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(first);
        mockMvc.perform(get("/api/users/me").header("Authorization", auth(me))).andExpect(jsonPath("$.avatarUrl").value(firstUrl));

        String secondUrl = mapper.readTree(mockMvc.perform(upload(me, dataUrl("image/jpeg", jpeg(3_000)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("avatarUrl").asString();
        assertThat(secondUrl).isNotEqualTo(firstUrl);                         // 새 사진은 새 주소: 이전 캐시가 남지 않는다
        mockMvc.perform(get(firstUrl)).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/users/me/avatar").header("Authorization", auth(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").doesNotExist());
        mockMvc.perform(get(secondUrl)).andExpect(status().isNotFound());
    }

    @Test
    void onlyRealSmallPicturesAreAccepted() throws Exception {
        UserAccount me = user("사진 주인");
        String png = dataUrl("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
        mockMvc.perform(upload(me, png)).andExpect(status().isOk());

        String[] bad = {
                "not a data url",
                dataUrl("image/svg+xml", "<svg onload=alert(1)/>".getBytes()),            // SVG는 스크립트를 담을 수 있어 받지 않는다
                dataUrl("image/jpeg", "<html>".getBytes()),                               // 형식을 속인 파일
                dataUrl("image/png", jpeg(500)),                                          // 선언과 다른 파일
                dataUrl("image/jpeg", jpeg(150_001)),                                     // 너무 큼
                "data:image/jpeg;base64,@@@@",                                           // base64 아님
        };
        for (String image : bad) {
            mockMvc.perform(upload(me, image)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AVATAR"));
        }
        assertThat(avatarRepository.findKeyByUserId(me.getId())).isPresent();           // 기존 사진은 그대로
        mockMvc.perform(get("/api/public/avatars/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void withdrawingAMemberDeletesTheirPhoto() throws Exception {
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영자", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        UserAccount leaving = user("떠나는 회원");
        mockMvc.perform(upload(leaving, dataUrl("image/jpeg", jpeg(1_000)))).andExpect(status().isOk());
        assertThat(avatarRepository.findKeyByUserId(leaving.getId())).isPresent();

        adminUserService.withdraw(admin.getId(), leaving.getId(), true);

        assertThat(avatarRepository.findKeyByUserId(leaving.getId())).isEmpty();
    }
}
