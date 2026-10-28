package com.likelion.chak.repository;

import com.likelion.chak.domain.BenefitSubmission;
import com.likelion.chak.domain.PromotionInquiry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitSubmissionRepository extends JpaRepository<BenefitSubmission, Long> {

    Slice<BenefitSubmission> findAllByStatusOrderByCreatedAtDescIdDesc(PromotionInquiry.Status status, Pageable pageable);

    Slice<BenefitSubmission> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
