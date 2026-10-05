package com.likelion.chak.dto;

import com.likelion.chak.domain.DeskObject;
import com.likelion.chak.domain.DeskObjectType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record DeskObjectResponse(
        DeskObjectType representationType,
        String assetId,
        String color,
        double x,
        double y,
        double rotation,
        double scale,
        int zIndex,
        JsonNode metadata) {

    public static DeskObjectResponse from(DeskObject object, ObjectMapper objectMapper) {
        JsonNode metadata = object.getMetadataPayload() == null
                ? null
                : objectMapper.readTree(object.getMetadataPayload());
        return new DeskObjectResponse(
                object.getRepresentationType(),
                object.getAssetId(),
                object.getColor(),
                object.getX(),
                object.getY(),
                object.getRotation(),
                object.getScale(),
                object.getZIndex(),
                metadata);
    }
}
