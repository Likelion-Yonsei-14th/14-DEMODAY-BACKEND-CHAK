package com.likelion.chak.controller;

import com.likelion.chak.alert.AlertNotifier;
import com.likelion.chak.config.JwtTokenProvider;
import com.likelion.chak.domain.BenefitSubmission;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.repository.BenefitSubmissionRepository;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BenefitSubmissionApiTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    private static final String LOGO_KEY = "promotion/uploads/0b1e2f3a-1111-2222-3333-444455556666/logo.png";

    @Autowired private MockMvc mockMvc;
    @Autowired private BenefitSubmissionRepository repository;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private JwtTokenProvider jwt;
    @MockitoBean private AlertNotifier notifier;
    @MockitoBean private S3Client s3Client;
    @MockitoBean private S3Presigner s3Presigner;

    private void stubPresigner() throws Exception {
        PresignedPutObjectRequest put = mock(PresignedPutObjectRequest.class);
        when(put.url()).thenReturn(URI.create("https://bucket.example/put").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(put);
        PresignedGetObjectRequest download = mock(PresignedGetObjectRequest.class);
        when(download.url()).thenReturn(URI.create("https://bucket.example/get").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(download);
    }

    private static String ip() {
        int n = NEXT_IP.getAndIncrement();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 가장 단순한 통과 답변(% 할인 + 수험표 제시 + 전 매장). 테스트마다 필요한 칸만 바꿔 쓴다. */
    private static String answers(String brand, String extra) {
        return "{\"brand\":\"" + brand + "\",\"category\":\"CAFE\",\"contactName\":\"김담당\",\"phone\":\"010-1234-5678\","
                + "\"email\":\"Brand@Example.COM\",\"target\":\"EXAM_TICKET_HOLDER\",\"benefits\":[\"PERCENT\"],\"percent\":20,"
                + "\"headlineRaw\":\"수험생 전 음료 20% 할인\",\"benefitDetail\":\"수험표를 보여주시면 할인해요\","
                + "\"startDate\":\"2026-11-20\",\"endDate\":\"2026-12-31\",\"usageMethods\":[\"SHOW_EXAM_TICKET\"],"
                + "\"scope\":\"ALL_STORES\",\"logoFileKey\":\"" + LOGO_KEY + "\",\"exposure\":[\"BENEFIT_BOX\"],"
                + "\"reviewPeriod\":\"WITHIN_1_DAY\"" + extra + "}";
    }

    private MockHttpServletRequestBuilder submit(String answersJson, String extra) {
        return post("/api/public/promotion/benefit-submissions").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip()).content("{\"answers\":" + answersJson + extra + "}");
    }

    private BenefitSubmission find(String brand) {
        return repository.findAll().stream().filter(s -> s.getBrand().equals(brand)).findFirst().orElseThrow();
    }

    @Test
    void validSubmissionIsStoredWithNormalizedAnswers() throws Exception {
        String brand = "혜택" + unique();
        mockMvc.perform(submit(answers(brand, ",\"unknownField\":\"x\""), ""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.received").value(true));
        BenefitSubmission saved = find(brand);
        assertThat(saved.getEmail()).isEqualTo("brand@example.com");
        assertThat(saved.getHeadline()).isEqualTo("수험생 전 음료 20% 할인");
        assertThat(saved.getAnswers()).contains("\"percent\":20").doesNotContain("unknownField");
    }

    @Test
    void chosenBenefitRequiresItsOwnFields() throws Exception {
        String missingPercent = answers("누락" + unique(), "").replace(",\"percent\":20", "");
        mockMvc.perform(submit(missingPercent, "")).andExpect(status().isBadRequest());

        String badPercent = answers("범위" + unique(), "").replace("\"percent\":20", "\"percent\":101");
        mockMvc.perform(submit(badPercent, "")).andExpect(status().isBadRequest());

        String stringNumber = answers("글자" + unique(), "").replace("\"percent\":20", "\"percent\":\"20퍼\"");
        mockMvc.perform(submit(stringNumber, "")).andExpect(status().isBadRequest());
    }

    @Test
    void specialPriceMustBeBelowRegularPrice() throws Exception {
        String base = answers("특별가" + unique(), "").replace("[\"PERCENT\"],\"percent\":20", "[\"SPECIAL_PRICE\"]");
        mockMvc.perform(submit(base.replace("\"headlineRaw\"", "\"regularPrice\":5000,\"specialPrice\":5000,\"headlineRaw\""), ""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(submit(base.replace("\"headlineRaw\"", "\"regularPrice\":5000,\"specialPrice\":4000,\"headlineRaw\""), ""))
                .andExpect(status().isCreated());
    }

    @Test
    void headlineIsLimitedToTwentyCharactersAndPeriodMustBeOrdered() throws Exception {
        mockMvc.perform(submit(answers("긴문구" + unique(), "").replace("수험생 전 음료 20% 할인", "가".repeat(21)), ""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(submit(answers("기간" + unique(), "").replace("2026-12-31", "2026-11-01"), ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void couponCodeQuestionAppearsOnlyForCodeMethods() throws Exception {
        String app = answers("앱" + unique(), "").replace("[\"SHOW_EXAM_TICKET\"]", "[\"REGISTER_IN_APP\"]")
                .replace("\"scope\"", "\"appName\":\"메가앱\",\"scope\"");
        mockMvc.perform(submit(app, "")).andExpect(status().isBadRequest());
        mockMvc.perform(submit(app.replace("\"scope\"", "\"couponIssue\":\"SHARED_CODE\",\"couponCode\":\"CHAK2027\",\"scope\""), ""))
                .andExpect(status().isCreated());
        mockMvc.perform(submit(app.replace("\"scope\"", "\"couponIssue\":\"UNIQUE_CODES\",\"couponQuantity\":100,\"scope\""), ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void someStoresRequireAStoreListAndFileKeysMustBeOurs() throws Exception {
        String some = answers("일부" + unique(), "").replace("ALL_STORES", "SOME_STORES");
        mockMvc.perform(submit(some, "")).andExpect(status().isBadRequest());
        mockMvc.perform(submit(some.replace("\"scope\"", "\"storeList\":\"강남역점\",\"scope\""), "")).andExpect(status().isCreated());

        String foreign = answers("외부" + unique(), "").replace(LOGO_KEY, "../../etc/passwd");
        mockMvc.perform(submit(foreign, "")).andExpect(status().isBadRequest());
    }

    @Test
    void honeypotIsAcceptedButNotStored() throws Exception {
        String bot = "봇" + unique();
        mockMvc.perform(submit(answers(bot, ""), ",\"website\":\"http://spam\"")).andExpect(status().isCreated());
        assertThat(repository.findAll().stream().anyMatch(s -> s.getBrand().equals(bot))).isFalse();
    }

    @Test
    void uploadGivesAKeyUnderOurPrefixAndRejectsBadFiles() throws Exception {
        stubPresigner();
        String url = "/api/public/promotion/benefit-uploads";
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", ip())
                        .content("{\"purpose\":\"LOGO\",\"fileName\":\"메가 로고.PNG\",\"size\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.matchesPattern("promotion/uploads/[0-9a-f-]{36}/_____.png")))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/png"));
        for (String bad : new String[]{
                "{\"purpose\":\"LOGO\",\"fileName\":\"run.exe\",\"size\":1000}",
                "{\"purpose\":\"SHEET\",\"fileName\":\"logo.png\",\"size\":1000}",
                "{\"purpose\":\"LOGO\",\"fileName\":\"logo.png\",\"size\":" + (20L * 1024 * 1024 + 1) + "}",
                "{\"purpose\":\"LOGO\",\"fileName\":\"logo.png\"}"}) {
            mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", ip()).content(bad))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void onlyAdminsReadAndUpdateSubmissions() throws Exception {
        stubPresigner();
        UserAccount member = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "회원", null, null));
        UserAccount admin = userRepository.save(UserAccount.createKakao("k-" + UUID.randomUUID(), "운영", null, null));
        admin.updateAdmin(true);
        userRepository.save(admin);
        mockMvc.perform(get("/api/admin/benefit-submissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/benefit-submissions").header("Authorization", "Bearer " + jwt.createAccessToken(member)))
                .andExpect(status().isForbidden());

        String brand = "어드민" + unique();
        mockMvc.perform(submit(answers(brand, ""), "")).andExpect(status().isCreated());
        Long id = find(brand).getId();
        String auth = "Bearer " + jwt.createAccessToken(admin);
        mockMvc.perform(get("/api/admin/benefit-submissions?status=NEW&size=100").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.brand=='" + brand + "')]").isNotEmpty());
        mockMvc.perform(get("/api/admin/benefit-submissions/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answers.percent").value(20))
                .andExpect(jsonPath("$.files[0].field").value("logoFileKey"));
        mockMvc.perform(patch("/api/admin/benefit-submissions/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED"));
        mockMvc.perform(get("/api/admin/benefit-submissions/99999999").header("Authorization", auth))
                .andExpect(status().isNotFound());
    }
}
