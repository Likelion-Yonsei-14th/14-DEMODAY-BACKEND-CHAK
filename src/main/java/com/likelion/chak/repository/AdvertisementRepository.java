package com.likelion.chak.repository;

import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.Advertisement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    List<Advertisement> findAllByPlacementAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByIdDesc(
            AdPlacement placement,
            Instant startsAt,
            Instant endsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Advertisement> findForUpdateById(Long id);
}
