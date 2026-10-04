package com.likelion.chak.dto;

import com.likelion.chak.domain.GuestIdentity;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GuestSession {

    private GuestIdentity guestIdentity;
    private boolean created;
}
