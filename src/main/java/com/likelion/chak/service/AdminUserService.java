package com.likelion.chak.service;

import com.likelion.chak.domain.AdminAuditLog;
import com.likelion.chak.domain.AuthProvider;
import com.likelion.chak.domain.ChaltteokWallet;
import com.likelion.chak.domain.MessageStatus;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AdminDtos.AuditEntry;
import com.likelion.chak.dto.AdminDtos.UserDetail;
import com.likelion.chak.dto.AdminDtos.UserSummary;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdminAuditLogRepository;
import com.likelion.chak.repository.ChaltteokWalletRepository;
import com.likelion.chak.repository.MessageRepository;
import com.likelion.chak.repository.MinigameProfileRepository;
import com.likelion.chak.repository.UserAvatarRepository;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.PersonalMessageDeliveryRepository;
import com.likelion.chak.repository.RefreshTokenRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** 회원 조회와 탈퇴 처리. 탈퇴는 행을 지우지 않고 개인정보만 지우는 익명화다(응원·결제 장부 보존). */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final UserAccountRepository userRepository;
    private final PersonalDeskRepository deskRepository;
    private final PersonalMessageDeliveryRepository deliveryRepository;
    private final MessageRepository messageRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ChaltteokWalletRepository walletRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final MinigameProfileRepository minigameProfileRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final com.likelion.chak.push.PushSubscriptionRepository pushSubscriptionRepository;

    @Transactional(readOnly = true)
    public SliceResponse<UserSummary> list(String query, String state, int page, int size) {
        String normalizedState = switch (state == null ? "ACTIVE" : state.toUpperCase(Locale.ROOT)) {
            case "ALL" -> "ALL";
            case "DELETED" -> "DELETED";
            default -> "ACTIVE";
        };
        String trimmed = query == null ? "" : query.strip();
        String pattern = trimmed.isEmpty() ? "" : "%" + trimmed.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        long idValue = trimmed.matches("^\\d{1,18}$") ? Long.parseLong(trimmed) : -1L;
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<UserAccount> result = userRepository.search(
                AuthProvider.TEST, normalizedState, pattern, idValue, PageRequest.of(Math.max(page, 0), safeSize,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("createdAt"),
                                org.springframework.data.domain.Sort.Order.desc("id"))));
        return new SliceResponse<>(
                result.getContent().stream().map(AdminUserService::summary).toList(),
                result.getNumber(), result.getSize(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public UserDetail detail(Long userId) {
        UserAccount user = find(userId);
        PersonalDesk desk = deskRepository.findByOwnerId(userId).orElse(null);
        long received = desk == null ? 0 : deliveryRepository.countByDeskAndMessageStatusNot(desk, MessageStatus.DELETED);
        long sent = messageRepository.countByAuthorUserIdAndStatusNot(userId, MessageStatus.DELETED);
        long balance = walletRepository.findByUserId(userId).map(ChaltteokWallet::getBalance).orElse(0L);
        List<AuditEntry> history = auditLogRepository
                .findTop20ByTargetTypeAndTargetIdOrderByCreatedAtDesc("USER", userId).stream()
                .map(a -> new AuditEntry(a.getAction(), a.getAdmin().getId(), a.getDetail(), a.getCreatedAt()))
                .toList();
        return new UserDetail(summary(user), desk == null ? null : desk.getId(),
                desk != null && desk.isRoomClosed(), received, sent, balance, history);
    }

    /**
     * 탈퇴 처리. 개인정보를 지우고 로그인 식별자를 바꿔 같은 계정으로 다시 가입하면 새 회원이 되게 한다.
     * 보낸 응원은 남기되 작성자를 "탈퇴한 사용자"로 바꾸고, 받은 책상은 닫는다. 이미 처리된 회원은 그대로 성공한다.
     * 남은 찰떡이 있으면 소멸에 동의했을 때만 진행한다(장부는 법정 보존 기간 동안 남는다).
     */
    @Transactional
    public UserDetail withdraw(Long adminId, Long userId, boolean confirmForfeitBalance) {
        UserAccount admin = find(adminId);
        UserAccount user = userRepository.findForUpdateById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getProvider() == AuthProvider.TEST) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.isDeleted()) {
            return detail(userId);
        }
        if (user.isAdmin() || user.getId().equals(adminId)) {
            throw new CustomException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        long balance = walletRepository.findByUserId(userId).map(ChaltteokWallet::getBalance).orElse(0L);
        if (balance > 0 && !confirmForfeitBalance) {
            throw new CustomException(ErrorCode.BALANCE_REMAINING);
        }

        Instant now = Instant.now();
        user.anonymize(now);
        refreshTokenRepository.deleteAllByUserId(userId);
        minigameProfileRepository.deleteById(userId);
        userAvatarRepository.deleteById(userId);
        pushSubscriptionRepository.deleteAllByUserId(userId);
        messageRepository.anonymizeAuthor(userId, UserAccount.WITHDRAWN_NAME);
        deskRepository.findForUpdateByOwnerId(userId).ifPresent(PersonalDesk::closeForWithdrawnOwner);
        auditLogRepository.save(new AdminAuditLog(admin, "WITHDRAW_USER", "USER", userId,
                balance > 0 ? "남은 찰떡 " + balance + "개 소멸 동의" : null));
        log.info("User withdrawn by admin: target={} admin={}", userId, adminId);
        return detail(userId);
    }

    private UserAccount find(Long userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getProvider() == AuthProvider.TEST) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private static UserSummary summary(UserAccount user) {
        return new UserSummary(user.getId(), user.getProvider(), user.getDisplayName(), user.getEmail(),
                user.getProfileImageUrl(), user.isAdmin(), user.getCreatedAt(), user.getLastLoginAt(), user.getDeletedAt());
    }
}
