package com.likelion.chak.controller;

import com.likelion.chak.service.ProfileService;
import com.likelion.chak.service.ProfileService.Avatar;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 회원이 올린 프로필 사진. 이미지 태그가 로그인 헤더를 보낼 수 없어서 로그인 없이 읽을 수 있고,
 * 대신 주소(키)가 올릴 때마다 새로 만드는 난수라서 주소를 아는 사람만 볼 수 있다.
 */
@RestController
@RequestMapping("/api/public/avatars")
@RequiredArgsConstructor
public class AvatarController {

    private final ProfileService profileService;

    @GetMapping("/{avatarKey}")
    public ResponseEntity<byte[]> get(@PathVariable("avatarKey") String avatarKey) {
        Avatar avatar = profileService.avatar(avatarKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(avatar.bytes());
    }
}
