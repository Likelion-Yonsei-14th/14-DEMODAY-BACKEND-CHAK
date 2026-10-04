package com.likelion.chak.repository;

import com.likelion.chak.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByKakaoId(String kakaoId);
}
