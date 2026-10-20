package com.likelion.chak.repository;

import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    long countByDeletedAtIsNull();

    @org.springframework.data.jpa.repository.Query("select user.createdAt from UserAccount user where user.createdAt >= :from and user.createdAt < :to")
    java.util.List<java.time.Instant> findCreatedAtBetween(
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to);

    /**
     * 회원 검색. state는 ACTIVE(탈퇴 안 함) / DELETED(탈퇴) / ALL, pattern은 소문자 like 패턴(없으면 빈 문자열),
     * idValue는 숫자로 입력했을 때 그 id(아니면 -1).
     */
    @org.springframework.data.jpa.repository.Query("""
            select user from UserAccount user
            where (:state = 'ALL'
                   or (:state = 'DELETED' and user.deletedAt is not null)
                   or (:state = 'ACTIVE' and user.deletedAt is null))
              and (:pattern = ''
                   or lower(user.displayName) like :pattern
                   or lower(coalesce(user.email, '')) like :pattern
                   or user.providerUserId like :pattern
                   or user.id = :idValue)
            """)
    org.springframework.data.domain.Page<UserAccount> search(
            @org.springframework.data.repository.query.Param("state") String state,
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            @org.springframework.data.repository.query.Param("idValue") long idValue,
            org.springframework.data.domain.Pageable pageable);

    Optional<UserAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findForUpdateById(Long id);
}
