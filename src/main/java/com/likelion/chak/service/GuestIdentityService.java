package com.likelion.chak.service;

import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.repository.GuestIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestIdentityService {

    private final GuestIdentityRepository guestIdentityRepository;

    @Transactional
    public GuestSession resolveOrCreate(String guestKey) {
        if (guestKey != null && !guestKey.isBlank()) {
            return guestIdentityRepository.findByGuestKey(guestKey)
                    .map(guest -> new GuestSession(guest, false))
                    .orElseGet(this::createGuestSession);
        }

        return createGuestSession();
    }

    private GuestSession createGuestSession() {
        GuestIdentity guest = guestIdentityRepository.save(GuestIdentity.create());
        return new GuestSession(guest, true);
    }
}
