package com.likelion.chak.repository;

import com.likelion.chak.domain.StoreOutreach;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreOutreachRepository extends JpaRepository<StoreOutreach, Long> {

    List<StoreOutreach> findAllByOrderByUpdatedAtDescIdDesc();
}
