package com.likelion.chak.service;

import com.likelion.chak.domain.AdminAuditLog;
import com.likelion.chak.domain.Discount;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DiscountDtos.DiscountList;
import com.likelion.chak.dto.DiscountDtos.DiscountRequest;
import com.likelion.chak.dto.DiscountDtos.DiscountResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.AdminAuditLogRepository;
import com.likelion.chak.repository.DiscountRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 수험표 할인 모음(혜택함). 사용자에게는 켜 둔 혜택만 보여 주고(끝난 혜택은 화면에서 거른다),
 * 운영자는 어드민에서 추가·수정·숨김·삭제한다. 고칠 때마다 누가 고쳤는지 남긴다.
 */
@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final UserAccountRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public DiscountList listPublic() {
        List<DiscountResponse> items = discountRepository.findAllByVisibleTrueOrderByIdAsc().stream()
                .map(DiscountResponse::forPublic).toList();
        return new DiscountList(discountRepository.findLastUpdatedAt(), items);
    }

    @Transactional(readOnly = true)
    public List<DiscountResponse> listAll() {
        return discountRepository.findAllByOrderByUpdatedAtDescIdDesc().stream().map(DiscountResponse::from).toList();
    }

    @Transactional
    public DiscountResponse create(Long adminId, DiscountRequest request) {
        UserAccount admin = admin(adminId);
        validateDates(request);
        Discount discount = new Discount(request.brand().strip());
        apply(discount, request, admin);
        discountRepository.save(discount);
        auditLogRepository.save(new AdminAuditLog(admin, "DISCOUNT_CREATE", "DISCOUNT", discount.getId(), discount.getBrand()));
        return DiscountResponse.from(discount);
    }

    @Transactional
    public DiscountResponse update(Long adminId, Long discountId, DiscountRequest request) {
        UserAccount admin = admin(adminId);
        validateDates(request);
        Discount discount = find(discountId);
        apply(discount, request, admin);
        auditLogRepository.save(new AdminAuditLog(admin, "DISCOUNT_UPDATE", "DISCOUNT", discountId,
                discount.getBrand() + (discount.isVisible() ? "" : " (숨김)")));
        return DiscountResponse.from(discount);
    }

    @Transactional
    public void delete(Long adminId, Long discountId) {
        UserAccount admin = admin(adminId);
        Discount discount = find(discountId);
        discountRepository.delete(discount);
        auditLogRepository.save(new AdminAuditLog(admin, "DISCOUNT_DELETE", "DISCOUNT", discountId, discount.getBrand()));
    }

    private void apply(Discount discount, DiscountRequest request, UserAccount admin) {
        List<String> bring = request.bring() == null ? List.of()
                : request.bring().stream().filter(item -> item != null && !item.isBlank()).map(String::strip).toList();
        // 이름·내용이 모두 빈 항목(목록 안의 null 포함)은 빼고, 앞뒤 공백을 지워 저장한다.
        List<Discount.Extra> extras = request.extras() == null ? List.of()
                : request.extras().stream()
                        .filter(extra -> extra != null && !(isBlank(extra.label()) && isBlank(extra.value())))
                        .map(extra -> new Discount.Extra(extra.label().strip(), extra.value().strip()))
                        .toList();
        discount.update(request.brand().strip(), request.category().strip(), request.benefit().strip(), request.start(),
                request.end(), blankToNull(request.conditions()), request.area().strip(), blankToNull(request.place()),
                request.sourceUrl().strip(), request.sourceDate(), blankToNull(request.highlight()), bring,
                blankToNull(request.howTo()), extras, blankToNull(request.companions()), blankToNull(request.branches()),
                blankToNull(request.image()), blankToNull(request.imageSource()),
                request.visible() == null || request.visible(), admin.getDisplayName());
    }

    private static void validateDates(DiscountRequest request) {
        if (request.start() != null && request.end() != null && request.start().isAfter(request.end())) {
            throw new CustomException(ErrorCode.INVALID_DISCOUNT_PERIOD);
        }
    }

    private UserAccount admin(Long adminId) {
        return userRepository.findById(adminId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Discount find(Long discountId) {
        return discountRepository.findById(discountId).orElseThrow(() -> new CustomException(ErrorCode.DISCOUNT_NOT_FOUND));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
