package com.likelion.chak.service;

import com.likelion.chak.domain.AdminAuditLog;
import com.likelion.chak.domain.StoreOutreach;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.OutreachDtos.OutreachRequest;
import com.likelion.chak.dto.OutreachDtos.OutreachResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdminAuditLogRepository;
import com.likelion.chak.repository.StoreOutreachRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 팀원이 함께 쓰는 매장 연락 기록. 수정할 때마다 누가 고쳤는지 남긴다. */
@Service
@RequiredArgsConstructor
public class StoreOutreachService {

    private final StoreOutreachRepository outreachRepository;
    private final UserAccountRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<OutreachResponse> list() {
        return outreachRepository.findAllByOrderByUpdatedAtDescIdDesc().stream().map(OutreachResponse::from).toList();
    }

    @Transactional
    public OutreachResponse create(Long adminId, OutreachRequest request) {
        UserAccount admin = admin(adminId);
        StoreOutreach outreach = new StoreOutreach(request.storeName().strip(), request.channel());
        apply(outreach, request, admin);
        outreachRepository.save(outreach);
        auditLogRepository.save(new AdminAuditLog(admin, "STORE_OUTREACH_CREATE", "STORE_OUTREACH", outreach.getId(), outreach.getStatus().name()));
        return OutreachResponse.from(outreach);
    }

    @Transactional
    public OutreachResponse update(Long adminId, Long outreachId, OutreachRequest request) {
        UserAccount admin = admin(adminId);
        StoreOutreach outreach = find(outreachId);
        apply(outreach, request, admin);
        auditLogRepository.save(new AdminAuditLog(admin, "STORE_OUTREACH_UPDATE", "STORE_OUTREACH", outreachId, outreach.getStatus().name()));
        return OutreachResponse.from(outreach);
    }

    @Transactional
    public void delete(Long adminId, Long outreachId) {
        UserAccount admin = admin(adminId);
        StoreOutreach outreach = find(outreachId);
        outreachRepository.delete(outreach);
        auditLogRepository.save(new AdminAuditLog(admin, "STORE_OUTREACH_DELETE", "STORE_OUTREACH", outreachId, outreach.getStoreName()));
    }

    private void apply(StoreOutreach outreach, OutreachRequest request, UserAccount admin) {
        outreach.update(request.kind(), request.storeName().strip(), blankToNull(request.category()), blankToNull(request.region()),
                request.channel(), blankToNull(request.contact()), blankToNull(request.assignee()), request.status(),
                blankToNull(request.note()), request.lastContactedOn(), admin.getDisplayName());
    }

    private UserAccount admin(Long adminId) {
        return userRepository.findById(adminId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private StoreOutreach find(Long outreachId) {
        return outreachRepository.findById(outreachId).orElseThrow(() -> new CustomException(ErrorCode.OUTREACH_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
