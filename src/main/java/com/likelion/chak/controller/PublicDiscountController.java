package com.likelion.chak.controller;

import com.likelion.chak.dto.DiscountDtos.DiscountList;
import com.likelion.chak.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** 수험표 할인 모음(혜택함). 로그인 없이 본다. 운영자가 고친 내용이 곧 보이도록 캐시는 짧게. */
@RestController
@RequestMapping("/api/public/discounts")
@RequiredArgsConstructor
public class PublicDiscountController {

    private final DiscountService discountService;

    @GetMapping
    public ResponseEntity<DiscountList> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .body(discountService.listPublic());
    }
}
