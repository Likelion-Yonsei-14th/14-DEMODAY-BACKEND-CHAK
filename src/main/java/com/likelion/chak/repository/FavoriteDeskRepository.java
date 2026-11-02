package com.likelion.chak.repository;

import com.likelion.chak.domain.FavoriteDesk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteDeskRepository extends JpaRepository<FavoriteDesk, Long> {

    /** 지워지지 않은 책상만, 최근에 담은 순서로. */
    @Query("""
            select f from FavoriteDesk f join fetch f.desk d
            where f.user.id = :userId and d.deletedAt is null
            order by f.createdAt desc, f.id desc
            """)
    List<FavoriteDesk> findAllLiveByUserId(@Param("userId") Long userId);

    @Query("select count(f) from FavoriteDesk f where f.user.id = :userId and f.desk.deletedAt is null")
    long countLiveByUserId(@Param("userId") Long userId);

    Optional<FavoriteDesk> findByUserIdAndDeskId(Long userId, Long deskId);

    void deleteAllByUserId(Long userId);
}
