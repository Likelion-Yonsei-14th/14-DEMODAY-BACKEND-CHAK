package com.likelion.chak.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BasketRequest(
        @NotEmpty @Size(max = 100) List<@NotNull Long> deliveryIds) {
}
