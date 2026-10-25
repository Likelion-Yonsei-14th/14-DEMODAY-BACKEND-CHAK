package com.likelion.chak.repository;

import com.likelion.chak.domain.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {

    Optional<UserAvatar> findByAvatarKey(String avatarKey);

    /** 사진 본문을 읽지 않고 주소 키만 가져온다. 로그인·내 정보 응답마다 쓰인다. */
    @Query("select a.avatarKey from UserAvatar a where a.userId = :userId")
    Optional<String> findKeyByUserId(Long userId);
}
