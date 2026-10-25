package com.likelion.chak.push;

import java.util.Set;

/** 책상 주인이 답장을 보냈다. 받을 사람(편지를 쓴 회원·비회원)과 답장 목록 주소를 담는다(답장 내용은 담지 않는다). */
public record ReplyCreatedEvent(String supporterToken, Set<Long> userIds, Set<Long> guestIds) {
}
