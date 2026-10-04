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
}
