package com.likelion.chak.service;

import com.likelion.chak.domain.GuestIdentity;
import com.likelion.chak.domain.Message;
import com.likelion.chak.domain.UserAccount;

/** 요청한 사람: 로그인 회원 또는 쿠키로 식별되는 비회원. 둘 다 없을 수 있다. */
public record CallerIdentity(UserAccount user, GuestIdentity guest) {

    public boolean isPresent() {
        return user != null || guest != null;
    }

    public Long userId() {
        return user == null ? null : user.getId();
    }

    public Long guestId() {
        return guest == null ? null : guest.getId();
    }

    public boolean isAuthorOf(Message message) {
        if (user != null && message.getAuthorUser() != null) {
            return user.getId().equals(message.getAuthorUser().getId());
        }
        if (guest != null && message.getAuthorGuest() != null) {
            return guest.getId().equals(message.getAuthorGuest().getId());
        }
        return false;
    }
}
