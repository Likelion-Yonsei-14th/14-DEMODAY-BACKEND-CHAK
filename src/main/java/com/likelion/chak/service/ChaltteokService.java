package com.likelion.chak.service;

import com.likelion.chak.domain.ChaltteokCharge;
import com.likelion.chak.domain.ChaltteokLedgerEntry;
import com.likelion.chak.domain.ChaltteokWallet;
import com.likelion.chak.domain.ChargeStatus;
import com.likelion.chak.domain.PaymentOrder;
import com.likelion.chak.domain.PaymentOrderItem;
import com.likelion.chak.domain.ProductCode;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.ChaltteokDtos.BalanceResponse;
import com.likelion.chak.dto.ChaltteokDtos.ChargeResponse;
import com.likelion.chak.dto.ChaltteokDtos.ConfirmResponse;
import com.likelion.chak.dto.ChaltteokDtos.LedgerResponse;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseItem;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.exception.InsufficientChaltteokException;
import com.likelion.chak.repository.ChaltteokChargeRepository;
import com.likelion.chak.repository.ChaltteokLedgerRepository;
import com.likelion.chak.repository.ChaltteokWalletRepository;
import com.likelion.chak.repository.PaymentOrderItemRepository;
import com.likelion.chak.repository.PaymentOrderRepository;
import com.likelion.chak.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 충전식 포인트(찰떡, 1원 = 1찰떡).
 *
 * <p>동시성·악용 방어 원칙
 * <ul>
 *   <li>잔액은 지갑 행 잠금 아래에서만 바꾸고, DB 제약(잔액 ≥ 0, 장부 부호·유일성)이 마지막 방어선이다.</li>
 *   <li>충전 승인은 선점(CAS) → 토스 호출(트랜잭션 밖) → 반영 3단계다. 외부 호출 동안 DB 잠금·커넥션을 쥐지 않고,
 *       같은 주문의 동시 승인은 하나만 토스를 호출한다.</li>
 *   <li>잠금 순서는 항상 충전 주문 → 지갑이다(구매는 지갑만). 순환이 없어 데드락이 생기지 않는다.</li>
 *   <li>멱등: 이미 반영된 충전은 처음 결과를 돌려주고, 구매는 Idempotency-Key로 이중 차감을 막는다.</li>
 * </ul>
 */
@Service
public class ChaltteokService {

    private static final Logger log = LoggerFactory.getLogger(ChaltteokService.class);

    static final long MIN_CHARGE = 300;
    static final long MAX_CHARGE = 50_000;
    /** 결제가 끝나지 않은 충전 주문을 한 사용자가 쌓을 수 있는 최대 개수(최근 1시간). */
    static final int MAX_PENDING_CHARGES = 10;
    /** 발급 후 한 번도 승인을 시도하지 않은 주문의 유효 시간. 토스 결제 세션보다 충분히 길다. */
    static final Duration CHARGE_TTL = Duration.ofHours(1);
    /** 승인 요청을 선점한 뒤 이 시간이 지나면 요청이 죽은 것으로 보고 다른 요청이 이어받을 수 있다. */
    static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(2);
    private static final String PROVIDER = "CHALTTEOK";

    private final ChaltteokWalletRepository walletRepository;
    private final ChaltteokChargeRepository chargeRepository;
    private final ChaltteokLedgerRepository ledgerRepository;
    private final PaymentOrderRepository orderRepository;
    private final PaymentOrderItemRepository itemRepository;
    private final UserAccountRepository userRepository;
    private final ChargeGateway gateway;
    private final TransactionTemplate tx;

    public ChaltteokService(
            ChaltteokWalletRepository walletRepository,
            ChaltteokChargeRepository chargeRepository,
            ChaltteokLedgerRepository ledgerRepository,
            PaymentOrderRepository orderRepository,
            PaymentOrderItemRepository itemRepository,
            UserAccountRepository userRepository,
            ObjectProvider<ChargeGateway> gatewayHolder,
            PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.chargeRepository = chargeRepository;
        this.ledgerRepository = ledgerRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.gateway = gatewayHolder.getIfAvailable();
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public BalanceResponse balance(Long userId) {
        ChaltteokWallet wallet = ensureWallet(userId, false);
        return new BalanceResponse(wallet.getBalance(), wallet.getCustomerKey());
    }

    @Transactional
    public ChargeResponse createCharge(Long userId, long amount) {
        if (amount < MIN_CHARGE || amount > MAX_CHARGE) {
            throw new CustomException(ErrorCode.INVALID_CHARGE_AMOUNT);
        }
        // 같은 사용자의 동시 주문 발급을 직렬화해 대기 주문 상한이 우회되지 않게 한다.
        ChaltteokWallet wallet = ensureWallet(userId, true);
        Instant now = Instant.now();
        long pending = chargeRepository.countByUserIdAndStatusAndCreatedAtAfter(
                userId, ChargeStatus.READY, now.minus(CHARGE_TTL));
        if (pending >= MAX_PENDING_CHARGES) {
            throw new CustomException(ErrorCode.TOO_MANY_PENDING_CHARGES);
        }
        String orderId = "chak-" + UUID.randomUUID().toString().replace("-", "");
        chargeRepository.save(ChaltteokCharge.create(wallet.getUser(), orderId, amount));
        return new ChargeResponse(orderId, amount, "찰떡 " + String.format("%,d", amount) + "개");
    }

    /**
     * 토스 승인 후 잔액에 반영한다. 이미 처리한 주문은 처음 결과를 그대로 돌려줘 이중 충전되지 않는다.
     * 이 메서드 자체는 트랜잭션이 아니다: 토스 호출 동안 DB 잠금·커넥션을 쥐지 않기 위해 단계별로 나눈다.
     */
    public ConfirmResponse confirmCharge(Long userId, String paymentKey, String orderId, long amount) {
        ConfirmResponse already = tx.execute(status -> claim(userId, orderId, amount));
        if (already != null) {
            return already;
        }
        try {
            if (gateway == null) {
                throw new CustomException(ErrorCode.PAYMENT_NOT_CONFIGURED);
            }
            gateway.confirm(paymentKey, orderId, amount);
            return tx.execute(status -> complete(userId, paymentKey, orderId, amount));
        } catch (RuntimeException e) {
            release(orderId);
            throw e;
        }
    }

    /** 1단계: 주문을 검증하고 승인 요청을 선점한다. 이미 끝난 주문이면 처음 결과를 돌려주고, 아니면 null. */
    private ConfirmResponse claim(Long userId, String orderId, long amount) {
        ChaltteokCharge charge = chargeRepository.findByOrderId(orderId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        if (charge.getAmount() != amount) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }
        if (charge.isDone()) {
            return new ConfirmResponse(orderId, charge.getAmount(), charge.getResultBalance());
        }
        Instant now = Instant.now();
        if (charge.isExpired(now, CHARGE_TTL)) {
            throw new CustomException(ErrorCode.ORDER_EXPIRED);
        }
        if (chargeRepository.claim(orderId, userId, now, now.minus(CLAIM_TIMEOUT)) != 1) {
            throw new CustomException(ErrorCode.CHARGE_IN_PROGRESS);
        }
        return null;
    }

    /** 3단계: 주문 행 → 지갑 순서로 잠그고 잔액·장부·주문 상태를 한 트랜잭션에서 반영한다. */
    private ConfirmResponse complete(Long userId, String paymentKey, String orderId, long amount) {
        ChaltteokCharge charge = chargeRepository.findForUpdateByOrderId(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        if (charge.isDone()) {
            // 선점이 만료돼 다른 요청이 먼저 끝낸 경우. 이중 반영하지 않고 그 결과를 돌려준다.
            return new ConfirmResponse(orderId, charge.getAmount(), charge.getResultBalance());
        }
        ChaltteokWallet wallet = ensureWallet(userId, true);
        wallet.credit(amount);
        ledgerRepository.save(ChaltteokLedgerEntry.charge(wallet.getUser(), amount, wallet.getBalance(), orderId));
        charge.complete(paymentKey, wallet.getBalance(), Instant.now());
        return new ConfirmResponse(orderId, amount, wallet.getBalance());
    }

    /**
     * 승인에 실패하면 선점을 풀어 같은 주문으로 다시 결제·확인할 수 있게 한다.
     * 풀지 못해도(DB 장애 등) 선점은 시간이 지나면 만료되므로 영구히 막히지 않는다.
     */
    private void release(String orderId) {
        try {
            tx.executeWithoutResult(status -> chargeRepository.release(orderId));
        } catch (RuntimeException e) {
            log.warn("Could not release charge claim: orderId={}", orderId, e);
        }
    }

    /**
     * 구매: 잔액에서 차감하고 결제 완료 주문을 만든다.
     * Idempotency-Key가 있으면 같은 키의 재요청은 새로 차감하지 않고 처음 결과를 돌려준다(더블클릭·재시도 방어).
     */
    @Transactional
    public PurchaseResponse purchase(Long userId, List<PurchaseItem> items, String idempotencyKey) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (PurchaseItem item : items) {
            String key = item.code().name() + "|" + (item.ref() == null ? "" : item.ref().trim());
            merged.merge(key, item.quantity() == null ? 1 : item.quantity(), Integer::sum);
        }
        long total = 0;
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            if (entry.getValue() > 100) {
                throw new CustomException(ErrorCode.INVALID_PURCHASE);
            }
            total += (long) ProductCode.valueOf(entry.getKey().split("\\|", -1)[0]).unitPrice() * entry.getValue();
        }
        String requestHash = hash(merged);

        // 지갑 잠금 뒤에 키를 확인해야 같은 키의 동시 요청이 직렬화된다.
        ChaltteokWallet wallet = ensureWallet(userId, true);
        if (idempotencyKey != null) {
            ChaltteokLedgerEntry previous = ledgerRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (previous != null) {
                if (!requestHash.equals(previous.getRequestHash())) {
                    throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                return new PurchaseResponse(-previous.getAmount(), previous.getBalanceAfter(), previous.orderIdFromRef());
            }
        }
        if (wallet.getBalance() < total) {
            throw new InsufficientChaltteokException(total, wallet.getBalance());
        }
        wallet.debit(total);

        PaymentOrder order = PaymentOrder.create(wallet.getUser(), null, Math.toIntExact(total), PROVIDER);
        order.markPaid("chaltteok-" + UUID.randomUUID(), Instant.now());
        order = orderRepository.save(order);
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            itemRepository.save(PaymentOrderItem.create(
                    order, ProductCode.valueOf(parts[0]), parts[1].isEmpty() ? null : parts[1], entry.getValue()));
        }
        ledgerRepository.save(ChaltteokLedgerEntry.purchase(
                wallet.getUser(), total, wallet.getBalance(), order.getId(), idempotencyKey, requestHash));
        return new PurchaseResponse(total, wallet.getBalance(), order.getId());
    }

    @Transactional(readOnly = true)
    public List<LedgerResponse> history(Long userId, int size) {
        return ledgerRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, size))
                .stream()
                .map(e -> new LedgerResponse(e.getType(), e.getAmount(), e.getBalanceAfter(), e.getRef(), e.getCreatedAt()))
                .toList();
    }

    /** 장부 합계와 지갑 잔액이 같은지. 운영 점검·테스트용 정합성 검증이다. */
    @Transactional(readOnly = true)
    public boolean isConsistent(Long userId) {
        long balance = walletRepository.findByUserId(userId).map(ChaltteokWallet::getBalance).orElse(0L);
        return balance == ledgerRepository.sumAmountByUserId(userId);
    }

    /**
     * 지갑을 (잠가서) 가져온다. 없으면 만든다. 생성은 INSERT IGNORE라 동시 생성이 예외 없이 한 건으로 합쳐진다.
     * 잠금 없이 읽기만 할 때(balance)는 lock=false.
     */
    private ChaltteokWallet ensureWallet(Long userId, boolean lock) {
        if (lock) {
            return walletRepository.findForUpdateByUserId(userId).orElseGet(() -> {
                createWallet(userId);
                return walletRepository.findForUpdateByUserId(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
            });
        }
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            createWallet(userId);
            return walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        });
    }

    private void createWallet(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        walletRepository.insertIfAbsent(userId, UUID.randomUUID().toString(), Instant.now());
    }

    private static String hash(Map<String, Integer> merged) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 정렬된 정규형으로 항목 순서가 달라도 같은 요청으로 본다.
            merged.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> digest.update((e.getKey() + "=" + e.getValue() + ";").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
