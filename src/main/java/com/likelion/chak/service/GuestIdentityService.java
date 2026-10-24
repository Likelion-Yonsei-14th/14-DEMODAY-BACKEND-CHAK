package com.likelion.chak.service;

import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.GuestIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestIdentityService {

    private final GuestIdentityRepository guestIdentityRepository;
    private final GuestCreationLimiter creationLimiter;

    @Transactional
    public GuestSession resolveOrCreate(String guestKey) {
        if (guestKey != null && !guestKey.isBlank()) {
            return guestIdentityRepository.findByGuestKey(guestKey)
                    .map(guest -> new GuestSession(guest, false))
                    .orElseGet(this::createGuestSession);
        }

        return createGuestSession();
    }

    /**
     * 방문·광고 기록처럼 실패해도 되는 요청용. 이미 있는 손님은 그대로 돌려주고, <b>새 손님을 만들어야 할 때만</b>
     * 접속자별 생성 속도를 확인해 넘으면 {@code 429}로 거절한다(손님 행을 무한정 만드는 남용 방지).
     * 응원 작성·결제처럼 사용자가 실제로 하는 일에는 쓰지 않는다.
     */
    @Transactional
    public GuestSession resolveOrCreateLimited(String guestKey, String clientIp) {
        if (guestKey != null && !guestKey.isBlank()) {
            var existing = guestIdentityRepository.findByGuestKey(guestKey);
            if (existing.isPresent()) {
                return new GuestSession(existing.get(), false);
            }
        }
        if (!creationLimiter.tryAcquire(clientIp)) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
        return createGuestSession();
    }

    private GuestSession createGuestSession() {
        GuestIdentity guest = guestIdentityRepository.save(GuestIdentity.create());
        return new GuestSession(guest, true);
    }
}
