package com.likelion.chak.dto;

import com.likelion.chak.domain.DeskObjectType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter
@NoArgsConstructor
public class DeskObjectRequest {

    private DeskObjectType representationType;
    private String assetId;
    private String color;
    private Double x;
    private Double y;
    private Double rotation;
    private Double scale;
    private Integer zIndex;
    private JsonNode metadata;
}
