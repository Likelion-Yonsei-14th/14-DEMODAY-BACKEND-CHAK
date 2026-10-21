package com.likelion.chak.service;

import com.likelion.chak.domain.AdminAuditLog;
import com.likelion.chak.domain.AdminInvite;
import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AdminDtos.AdminAccessResponse;
import com.likelion.chak.dto.AdminDtos.AdminInviteEntry;
import com.likelion.chak.dto.AdminDtos.UserSummary;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdminAuditLogRepository;
import com.likelion.chak.repository.AdminInviteRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 운영자 권한 관리. 이메일로 권한을 부여하며, 이미 가입한 회원이면 바로, 아직이면 초대로 저장했다가
 * 그 이메일의 구글 계정으로 처음 로그인할 때 적용한다. 구글이 확인한 이메일만 믿는다(카카오는 이메일을 받지 않는다).
 */
@Service
@RequiredArgsConstructor
public class AdminAccessService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccessService.class);

    private final UserAccountRepository userRepository;
    private final AdminInviteRepository inviteRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public AdminAccessResponse list() {
        List<UserSummary> admins = userRepository.findAllByAdminTrueAndDeletedAtIsNullOrderByCreatedAtAsc().stream()
                .map(AdminAccessService::summary).toList();
        List<AdminInviteEntry> invites = inviteRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(invite -> !invite.isConsumed())
                .map(invite -> new AdminInviteEntry(invite.getId(), invite.getEmail(), invite.getCreatedAt(),
                        invite.getConsumedAt(), invite.getCreatedBy() == null))
                .toList();
        return new AdminAccessResponse(admins, invites);
    }

    /** 이메일에 운영자 권한을 부여한다. 같은 요청을 반복해도 결과는 같다. */
    @Transactional
    public AdminAccessResponse grant(Long adminId, String rawEmail) {
        String email = normalize(rawEmail);
        UserAccount admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        List<UserAccount> existing = userRepository
                .findAllByProviderAndEmailIgnoreCaseAndDeletedAtIsNull(AuthProvider.GOOGLE, email);
        if (!existing.isEmpty()) {
            existing.forEach(user -> {
                if (!user.isAdmin()) {
                    user.updateAdmin(true);
                    auditLogRepository.save(new AdminAuditLog(admin, "GRANT_ADMIN", "USER", user.getId(), "이메일로 권한 부여"));
                    log.info("Admin granted: target={} by={}", user.getId(), adminId);
                }
            });
        } else if (inviteRepository.findByEmail(email).isEmpty()) {
            AdminInvite invite = inviteRepository.save(new AdminInvite(email, admin));
            auditLogRepository.save(new AdminAuditLog(admin, "INVITE_ADMIN", "ADMIN_INVITE", invite.getId(), "가입 전 이메일 초대"));
        }
        return list();
    }

    /** 운영자 권한을 회수한다. 본인은 회수할 수 없어 운영자가 아무도 남지 않는 일을 막는다. */
    @Transactional
    public AdminAccessResponse revoke(Long adminId, Long userId) {
        if (adminId.equals(userId)) {
            throw new CustomException(ErrorCode.ADMIN_SELF_REVOKE);
        }
        UserAccount admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        UserAccount target = userRepository.findForUpdateById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!target.isAdmin()) {
            throw new CustomException(ErrorCode.NOT_AN_ADMIN);
        }
        target.updateAdmin(false);
        auditLogRepository.save(new AdminAuditLog(admin, "REVOKE_ADMIN", "USER", userId, null));
        log.info("Admin revoked: target={} by={}", userId, adminId);
        return list();
    }

    /** 아직 적용되지 않은 초대를 취소한다. 이미 적용됐거나 없으면 아무것도 하지 않는다. */
    @Transactional
    public AdminAccessResponse cancelInvite(Long adminId, Long inviteId) {
        UserAccount admin = userRepository.findById(adminId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        inviteRepository.findById(inviteId).filter(invite -> !invite.isConsumed()).ifPresent(invite -> {
            inviteRepository.delete(invite);
            auditLogRepository.save(new AdminAuditLog(admin, "CANCEL_ADMIN_INVITE", "ADMIN_INVITE", inviteId, invite.getEmail()));
        });
        return list();
    }

    /**
     * 로그인할 때 호출한다. 구글이 확인한 이메일에 대한 초대가 남아 있으면 운영자로 만든다.
     * 카카오 등 이메일을 신뢰할 수 없는 제공자에는 적용하지 않는다.
     */
    @Transactional
    public void applyInviteOnLogin(UserAccount user) {
        if (user.getProvider() != AuthProvider.GOOGLE || user.getEmail() == null || user.isDeleted()) {
            return;
        }
        inviteRepository.findByEmail(user.getEmail().strip().toLowerCase(Locale.ROOT))
                .filter(invite -> !invite.isConsumed())
                .ifPresent(invite -> {
                    user.updateAdmin(true);
                    invite.consume(user, Instant.now());
                    log.info("Admin invite applied: user={}", user.getId());
                });
    }

    private static String normalize(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 320 || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }

    private static UserSummary summary(UserAccount user) {
        return new UserSummary(user.getId(), user.getProvider(), user.getDisplayName(), user.getEmail(),
                user.getProfileImageUrl(), user.isAdmin(), user.getCreatedAt(), user.getLastLoginAt(), user.getDeletedAt());
    }
}
