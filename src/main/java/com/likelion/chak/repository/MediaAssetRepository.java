package com.likelion.chak.repository;

import com.likelion.chak.domain.MediaAsset;
import com.likelion.chak.domain.MediaStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, MediaStatus status);

    @Query("select coalesce(sum(a.expectedSize), 0) from MediaAsset a where a.owner.id = :ownerId")
    long sumExpectedSizeByOwnerId(@Param("ownerId") Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MediaAsset> findForUpdateByIdAndOwnerId(Long id, Long ownerId);
}
