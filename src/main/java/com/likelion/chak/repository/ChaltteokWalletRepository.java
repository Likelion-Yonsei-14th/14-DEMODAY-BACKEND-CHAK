package com.likelion.chak.repository;

import com.likelion.chak.domain.ChaltteokWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ChaltteokWalletRepository extends JpaRepository<ChaltteokWallet, Long> {

    Optional<ChaltteokWallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChaltteokWallet> findForUpdateByUserId(Long userId);

    /**
     * 지갑이 없으면 만든다. 동시에 여러 요청이 와도 유니크 제약으로 한 건만 남고 예외를 던지지 않는다
     * (JPA 저장 후 예외를 잡는 방식은 트랜잭션을 rollback-only로 만들어 쓸 수 없다).
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO chaltteok_wallets (user_id, balance, customer_key, version, created_at, updated_at) "
            + "VALUES (:userId, 0, :customerKey, 0, :now, :now)", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("customerKey") String customerKey, @Param("now") Instant now);
}
