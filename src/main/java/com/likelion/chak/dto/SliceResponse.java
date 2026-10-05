package com.likelion.chak.dto;

import org.springframework.data.domain.Slice;

import java.util.List;

public record SliceResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext) {

    public static <T> SliceResponse<T> from(Slice<?> slice, List<T> content) {
        return new SliceResponse<>(content, slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
