package com.likelion.chak.service;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.GuestIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private final AuthService authService;
    private final GuestIdentityRepository guestIdentityRepository;

    /** 비회원 쿠키가 있어도 새 식별자를 만들지 않는다. 조회·수정 요청 전용. */
    @Transactional(readOnly = true)
    public CallerIdentity resolve(AuthenticatedUser principal, String guestKey) {
        if (principal != null) {
            UserAccount user = authService.getUser(principal.userId());
            return new CallerIdentity(user, null);
        }
        if (guestKey != null && !guestKey.isBlank()) {
            GuestIdentity guest = guestIdentityRepository.findByGuestKey(guestKey).orElse(null);
            return new CallerIdentity(null, guest);
        }
        return new CallerIdentity(null, null);
    }

    public CallerIdentity requireIdentity(AuthenticatedUser principal, String guestKey) {
        CallerIdentity identity = resolve(principal, guestKey);
        if (!identity.isPresent()) {
            throw new CustomException(ErrorCode.IDENTITY_REQUIRED);
        }
        return identity;
    }
}
