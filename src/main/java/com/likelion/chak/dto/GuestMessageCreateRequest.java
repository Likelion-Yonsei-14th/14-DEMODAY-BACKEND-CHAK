package com.likelion.chak.dto;

import com.likelion.chak.domain.MessageKind;
import com.likelion.chak.domain.MessageVisibility;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter
@NoArgsConstructor
public class GuestMessageCreateRequest {

    private String nickname;
    private MessageKind kind;
    private MessageVisibility visibility;
    private Integer schemaVersion;
    private JsonNode card;
    private DeskObjectRequest object;
    /** 아크릴 부적·보석 스티커 등 유료 요소가 있을 때 결제 완료된 주문 번호. */
    private Long paymentOrderId;
}
