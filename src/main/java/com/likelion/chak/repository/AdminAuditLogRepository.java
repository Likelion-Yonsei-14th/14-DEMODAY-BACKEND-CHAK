package com.likelion.chak.repository;

import com.likelion.chak.domain.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findTop20ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
