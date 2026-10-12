package com.likelion.chak.repository;

import com.likelion.chak.domain.AdUnlock;
import com.likelion.chak.domain.UnlockCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdUnlockRepository extends JpaRepository<AdUnlock, Long> {

    List<AdUnlock> findAllByUserId(Long userId);

    List<AdUnlock> findAllByGuestId(Long guestId);

    boolean existsByUserIdAndCategoryAndAssetId(Long userId, UnlockCategory category, String assetId);

    boolean existsByGuestIdAndCategoryAndAssetId(Long guestId, UnlockCategory category, String assetId);
}
