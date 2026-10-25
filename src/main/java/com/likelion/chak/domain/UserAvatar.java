package com.likelion.chak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 회원이 직접 올린 프로필 사진 한 장. 사진 파일 자체는 base64 글자로 저장한다. */
@Getter
@Entity
@Table(name = "user_avatars")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAvatar {

    @Id
    private Long userId;

    @Column(nullable = false, unique = true, length = 36)
    private String avatarKey;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String imageBase64;

    @Column(nullable = false)
    private Instant updatedAt;

    public UserAvatar(Long userId, String contentType, String imageBase64) {
        this.userId = userId;
        replace(contentType, imageBase64);
    }

    /** 새 사진으로 바꾼다. 주소(키)도 새로 만들어 이전 사진의 캐시가 남지 않게 한다. */
    public void replace(String contentType, String imageBase64) {
        this.avatarKey = UUID.randomUUID().toString();
        this.contentType = contentType;
        this.imageBase64 = imageBase64;
        this.updatedAt = Instant.now();
    }
}
