package com.likelion.chak.repository;

import com.likelion.chak.domain.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    List<Discount> findAllByOrderByUpdatedAtDescIdDesc();

    List<Discount> findAllByVisibleTrueOrderByIdAsc();

    List<Discount> findAllByVisibleTrueAndSponsoredTrue();

    @Query("select max(d.updatedAt) from Discount d")
    Instant findLastUpdatedAt();
}
