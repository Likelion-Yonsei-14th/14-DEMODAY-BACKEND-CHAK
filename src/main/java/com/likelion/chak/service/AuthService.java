package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AuthTokenResponse;
import com.likelion.chak.dto.OAuthLoginRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * 소셜 로그인. 계정은 (제공자, 제공자 식별자)로만 찾는다.
 * 제공자가 달라도 이메일이 같다고 계정을 합치지 않는다(검증되지 않은 이메일로 남의 계정을 가로채는 것을 막기 위해).
 */
@Service
public class AuthService {

    private final List<OAuthClient> clients;
    private final UserAccountRepository userAccountRepository;
    private final TokenService tokenService;
    private final AdminAccessService adminAccessService;
    private final String adminKakaoIds;
    private final String adminGoogleIds;
    private TransactionTemplate transactions;

    /** 제공자 HTTP 호출은 트랜잭션 밖에서 하고, DB 작업만 짧은 트랜잭션으로 묶는다(주입은 setter로 받아 생성자 호환을 지킨다). */
    @org.springframework.beans.factory.annotation.Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AuthService(
            List<OAuthClient> oauthClients,
            UserAccountRepository userAccountRepository,
            TokenService tokenService,
            AdminAccessService adminAccessService,
            @Value("${chak.admin-kakao-ids:}") String adminKakaoIds,
            @Value("${chak.admin-google-ids:}") String adminGoogleIds) {
        this.clients = oauthClients;
        this.userAccountRepository = userAccountRepository;
        this.tokenService = tokenService;
        this.adminAccessService = adminAccessService;
        this.adminKakaoIds = adminKakaoIds;
        this.adminGoogleIds = adminGoogleIds;
    }

    /**
     * 트랜잭션을 걸지 않는다. 걸면 제공자(카카오·구글) 호출을 기다리는 동안 DB 연결을 붙잡아, 아무 코드로 동시에
     * 로그인만 시도해도 연결 풀이 바닥난다. 제공자 확인이 끝난 뒤에만 짧은 트랜잭션으로 계정을 저장한다.
     */
    public AuthTokenResponse loginWithKakao(OAuthLoginRequest request) {
        return login(AuthProvider.KAKAO, request);
    }

    public AuthTokenResponse loginWithGoogle(OAuthLoginRequest request) {
        return login(AuthProvider.GOOGLE, request);
    }

    private AuthTokenResponse login(AuthProvider provider, OAuthLoginRequest request) {
        OAuthClient client = clients.stream()
                .filter(candidate -> candidate.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        OAuthProfile profile = client.authenticate(request.code(), request.redirectUri());
        return transactions.execute(status -> signIn(profile));
    }

    private AuthTokenResponse signIn(OAuthProfile profile) {
        UserAccount user = userAccountRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(existing -> updateProfile(existing, profile))
                .orElseGet(() -> userAccountRepository.save(UserAccount.create(
                        profile.provider(),
                        profile.providerUserId(),
                        profile.nickname(),
                        profile.email(),
                        profile.profileImageUrl())));

        // 설정(ADMIN_*_IDS)에 있는 계정은 항상 운영자. 그 밖의 권한은 어드민 화면에서 부여·회수하므로
        // 로그인할 때 덮어쓰지 않는다(예전처럼 매번 끄면 화면에서 부여한 권한이 사라진다).
        if (isConfiguredAdmin(profile)) {
            user.updateAdmin(true);
        }
        adminAccessService.applyInviteOnLogin(user);
        user.recordLogin(java.time.Instant.now());
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public UserAccount getUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserAccount getAdmin(Long userId) {
        UserAccount user = getUser(userId);
        if (!user.isAdmin()) {
            throw new CustomException(ErrorCode.ADMIN_REQUIRED);
        }
        return user;
    }

    private UserAccount updateProfile(UserAccount user, OAuthProfile profile) {
        user.updateProfile(profile.nickname(), profile.email(), profile.profileImageUrl());
        return user;
    }

    private boolean isConfiguredAdmin(OAuthProfile profile) {
        String configured = profile.provider() == AuthProvider.KAKAO ? adminKakaoIds : adminGoogleIds;
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .anyMatch(id -> !id.isEmpty() && id.equals(profile.providerUserId()));
    }
}
