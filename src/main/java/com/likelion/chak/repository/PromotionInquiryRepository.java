package com.likelion.chak.repository;

import com.likelion.chak.domain.PromotionInquiry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface PromotionInquiryRepository extends JpaRepository<PromotionInquiry, Long> {

    Slice<PromotionInquiry> findAllByStatusOrderByCreatedAtDescIdDesc(PromotionInquiry.Status status, Pageable pageable);

    Slice<PromotionInquiry> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    boolean existsByEmailIgnoreCaseAndCompanyIgnoreCaseAndCreatedAtAfter(String email, String company, Instant after);

    long countByStatus(PromotionInquiry.Status status);
}
