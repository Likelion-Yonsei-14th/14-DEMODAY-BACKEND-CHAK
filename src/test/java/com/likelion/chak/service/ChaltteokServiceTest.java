package com.likelion.chak.service;

import com.likelion.chak.domain.ChaltteokCharge;
import com.likelion.chak.domain.ProductCode;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.ChaltteokDtos.ChargeResponse;
import com.likelion.chak.dto.ChaltteokDtos.ConfirmResponse;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseItem;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.exception.InsufficientChaltteokException;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ChaltteokServiceTest {

    @Autowired private ChaltteokService chaltteokService;
    @Autowired private PaymentService paymentService;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private com.likelion.chak.repository.ChaltteokChargeRepository chargeRepository;

    private UserAccount user(String kakaoId) {
        return userAccountRepository.save(UserAccount.createKakao(kakaoId, "회원", null, null));
    }

    private long charge(UserAccount user, long amount) {
        ChargeResponse order = chaltteokService.createCharge(user.getId(), amount);
        return chaltteokService.confirmCharge(user.getId(), "pk-" + order.orderId(), order.orderId(), amount).balance();
    }

    @Test
    void newWalletStartsEmptyWithStableCustomerKey() {
        UserAccount user = user("w1");
        var first = chaltteokService.balance(user.getId());
        assertThat(first.balance()).isZero();
        assertThat(first.customerKey()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chaltteokService.balance(user.getId()).customerKey()).isEqualTo(first.customerKey());
    }

    @Test
    void chargeAmountIsLimited() {
        UserAccount user = user("w2");
        assertThatThrownBy(() -> chaltteokService.createCharge(user.getId(), 299))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CHARGE_AMOUNT));
        assertThatThrownBy(() -> chaltteokService.createCharge(user.getId(), 50_001))
                .isInstanceOf(CustomException.class);
        assertThat(chaltteokService.createCharge(user.getId(), 300).orderId()).hasSizeBetween(6, 64);
    }

    @Test
    void confirmCreditsOnceAndIsIdempotent() {
        UserAccount user = user("w3");
        ChargeResponse order = chaltteokService.createCharge(user.getId(), 1000);
        ConfirmResponse first = chaltteokService.confirmCharge(user.getId(), "pk", order.orderId(), 1000);
        ConfirmResponse again = chaltteokService.confirmCharge(user.getId(), "pk", order.orderId(), 1000);
        assertThat(first.balance()).isEqualTo(1000);
        assertThat(again).isEqualTo(first);
        assertThat(chaltteokService.balance(user.getId()).balance()).isEqualTo(1000);
    }

    @Test
    void confirmRejectsWrongAmountAndOtherUsersOrder() {
        UserAccount owner = user("w4");
        UserAccount other = user("w5");
        ChargeResponse order = chaltteokService.createCharge(owner.getId(), 1000);
        assertThatThrownBy(() -> chaltteokService.confirmCharge(owner.getId(), "pk", order.orderId(), 900))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_MISMATCH));
        assertThatThrownBy(() -> chaltteokService.confirmCharge(other.getId(), "pk", order.orderId(), 1000))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        assertThatThrownBy(() -> chaltteokService.confirmCharge(owner.getId(), "pk", "unknown-order", 1000))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void purchaseDebitsAndCreatesPaidOrderUsableOnce() {
        UserAccount user = user("w6");
        charge(user, 1000);
        PurchaseResponse purchase = chaltteokService.purchase(user.getId(), List.of(
                new PurchaseItem(ProductCode.CHARM_ACRYLIC, null, 2),
                new PurchaseItem(ProductCode.GEM, "ruby", 4)), null);
        assertThat(purchase.spent()).isEqualTo(120);
        assertThat(purchase.balance()).isEqualTo(880);

        CallerIdentity caller = new CallerIdentity(user, null);
        paymentService.consume(caller, purchase.orderId(), Map.of(ProductCode.CHARM_ACRYLIC, 1, ProductCode.GEM, 4));
        assertThatThrownBy(() -> paymentService.consume(caller, purchase.orderId(), Map.of(ProductCode.GEM, 1)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_USED));
    }

    @Test
    void purchaseWithoutEnoughBalanceReportsRequiredAndBalance() {
        UserAccount user = user("w7");
        charge(user, 300);
        assertThatThrownBy(() -> chaltteokService.purchase(user.getId(),
                List.of(new PurchaseItem(ProductCode.LOCKER_BULB, null, 2)), null))
                .isInstanceOfSatisfying(InsufficientChaltteokException.class, e -> {
                    assertThat(e.getRequired()).isEqualTo(400);
                    assertThat(e.getBalance()).isEqualTo(300);
                });
        assertThat(chaltteokService.balance(user.getId()).balance()).isEqualTo(300);
    }

    @Test
    void historyListsNewestFirst() {
        UserAccount user = user("w8");
        charge(user, 500);
        chaltteokService.purchase(user.getId(), List.of(new PurchaseItem(ProductCode.GEM, null, 1)), null);
        var history = chaltteokService.history(user.getId(), 10);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).amount()).isEqualTo(-5);
        assertThat(history.get(0).balanceAfter()).isEqualTo(495);
        assertThat(history.get(1).amount()).isEqualTo(500);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((CustomException) t).getErrorCode();
    }

    @Test
    void purchaseIdempotencyKeyReplaysInsteadOfDebitingTwice() {
        UserAccount user = user("w9");
        charge(user, 1000);
        List<PurchaseItem> items = List.of(new PurchaseItem(ProductCode.GEM, null, 10));
        PurchaseResponse first = chaltteokService.purchase(user.getId(), items, "key-12345678");
        PurchaseResponse replay = chaltteokService.purchase(user.getId(), items, "key-12345678");
        assertThat(replay).isEqualTo(first);
        assertThat(chaltteokService.balance(user.getId()).balance()).isEqualTo(950);
        assertThat(chaltteokService.isConsistent(user.getId())).isTrue();
    }

    @Test
    void sameIdempotencyKeyWithDifferentItemsIsRejected() {
        UserAccount user = user("w10");
        charge(user, 1000);
        chaltteokService.purchase(user.getId(), List.of(new PurchaseItem(ProductCode.GEM, null, 10)), "key-12345678");
        assertThatThrownBy(() -> chaltteokService.purchase(user.getId(),
                List.of(new PurchaseItem(ProductCode.GEM, null, 11)), "key-12345678"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void purchaseItemOrderDoesNotChangeIdempotencyHash() {
        UserAccount user = user("w11");
        charge(user, 1000);
        var a = new PurchaseItem(ProductCode.GEM, null, 1);
        var b = new PurchaseItem(ProductCode.PENNANT, "x", 1);
        PurchaseResponse first = chaltteokService.purchase(user.getId(), List.of(a, b), "key-abcdefgh");
        assertThat(chaltteokService.purchase(user.getId(), List.of(b, a), "key-abcdefgh")).isEqualTo(first);
    }

    @Test
    void invalidQuantitiesAreRejected() {
        UserAccount user = user("w12");
        charge(user, 1000);
        assertThatThrownBy(() -> chaltteokService.purchase(user.getId(), List.of(
                new PurchaseItem(ProductCode.GEM, "a", 100), new PurchaseItem(ProductCode.GEM, "a", 1)), null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_PURCHASE));
        assertThat(chaltteokService.balance(user.getId()).balance()).isEqualTo(1000);
    }

    @Test
    void pendingChargesAreCapped() {
        UserAccount user = user("w13");
        for (int i = 0; i < ChaltteokService.MAX_PENDING_CHARGES; i++) {
            chaltteokService.createCharge(user.getId(), 300);
        }
        assertThatThrownBy(() -> chaltteokService.createCharge(user.getId(), 300))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TOO_MANY_PENDING_CHARGES));
    }

    @Test
    void chargeBeingConfirmedElsewhereIsReportedInProgress() {
        UserAccount user = user("w14");
        ChargeResponse order = chaltteokService.createCharge(user.getId(), 1000);
        Instant now = Instant.now();
        assertThat(chargeRepository.claim(order.orderId(), user.getId(), now, now.minus(ChaltteokService.CLAIM_TIMEOUT)))
                .isEqualTo(1);
        assertThatThrownBy(() -> chaltteokService.confirmCharge(user.getId(), "pk", order.orderId(), 1000))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.CHARGE_IN_PROGRESS));
        // 선점한 요청이 죽어 시간이 지나면 다른 요청이 이어받을 수 있다.
        assertThat(chargeRepository.claim(order.orderId(), user.getId(), now,
                now.plus(ChaltteokService.CLAIM_TIMEOUT).plusSeconds(1))).isEqualTo(1);
    }

    @Test
    void unattemptedChargeExpiresButAttemptedOneDoesNot() {
        UserAccount user = user("w15");
        ChargeResponse order = chaltteokService.createCharge(user.getId(), 1000);
        ChaltteokCharge charge = chargeRepository.findByOrderId(order.orderId()).orElseThrow();
        Instant later = Instant.now().plus(ChaltteokService.CHARGE_TTL).plusSeconds(1);
        assertThat(charge.isExpired(later, ChaltteokService.CHARGE_TTL)).isTrue();
        Instant now = Instant.now();
        chargeRepository.claim(order.orderId(), user.getId(), now, now.minus(ChaltteokService.CLAIM_TIMEOUT));
        chargeRepository.release(order.orderId());
        ChaltteokCharge attempted = chargeRepository.findByOrderId(order.orderId()).orElseThrow();
        assertThat(attempted.isExpired(later, ChaltteokService.CHARGE_TTL)).isFalse();
    }

    @Test
    void ledgerAlwaysMatchesBalance() {
        UserAccount user = user("w16");
        charge(user, 5000);
        chaltteokService.purchase(user.getId(), List.of(new PurchaseItem(ProductCode.UNIVERSITY_CHARM, null, 3)), null);
        charge(user, 700);
        assertThat(chaltteokService.isConsistent(user.getId())).isTrue();
        assertThat(chaltteokService.balance(user.getId()).balance()).isEqualTo(5000 - 450 + 700);
    }
}
