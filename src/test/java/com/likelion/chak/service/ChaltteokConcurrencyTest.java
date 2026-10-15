package com.likelion.chak.service;

import com.likelion.chak.domain.ProductCode;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.ChaltteokDtos.ChargeResponse;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseItem;
import com.likelion.chak.dto.ChaltteokDtos.PurchaseResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.exception.InsufficientChaltteokException;
import com.likelion.chak.repository.ChaltteokLedgerRepository;
import com.likelion.chak.repository.ChaltteokWalletRepository;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 실제 스레드로 동시 요청을 보내 이중 충전·이중 차감·잔액 음수가 생기지 않는지 확인한다(트랜잭션 롤백 없이 커밋). */
@SpringBootTest
class ChaltteokConcurrencyTest {

    @Autowired private ChaltteokService chaltteokService;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ChaltteokWalletRepository walletRepository;
    @Autowired private ChaltteokLedgerRepository ledgerRepository;
    @MockitoBean private ChargeGateway gateway;

    private UserAccount user(String kakaoId) {
        return userAccountRepository.save(UserAccount.createKakao(kakaoId, "회원", null, null));
    }

    private void fund(UserAccount user, long amount) {
        ChargeResponse order = chaltteokService.createCharge(user.getId(), amount);
        chaltteokService.confirmCharge(user.getId(), "pk-" + order.orderId(), order.orderId(), amount);
    }

    private <T> List<Object> runParallel(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void parallelPurchasesNeverOverspend() throws Exception {
        UserAccount user = user("c1");
        fund(user, 300);
        // 100원(보석 20개)짜리를 20개 스레드가 동시에 산다 → 잔액 300으로는 정확히 3건만 성공해야 한다.
        List<Object> results = runParallel(20, () -> chaltteokService.purchase(
                user.getId(), List.of(new PurchaseItem(ProductCode.GEM, null, 20)), null));
        long ok = results.stream().filter(r -> r instanceof PurchaseResponse).count();
        long insufficient = results.stream().filter(r -> r instanceof InsufficientChaltteokException).count();
        assertThat(ok).isEqualTo(3);
        assertThat(insufficient).isEqualTo(17);
        assertThat(chaltteokService.balance(user.getId()).balance()).isZero();
        assertThat(chaltteokService.isConsistent(user.getId())).isTrue();
    }

    @Test
    void parallelConfirmsOfSameOrderCreditOnce() throws Exception {
        UserAccount user = user("c2");
        ChargeResponse order = chaltteokService.createCharge(user.getId(), 1000);
        doAnswer(inv -> {
            Thread.sleep(300); // 외부 승인이 느린 상황: 그동안 다른 요청이 몰린다.
            return null;
        }).when(gateway).confirm(any(), any(), anyLong());

        List<Object> results = runParallel(10, () ->
                chaltteokService.confirmCharge(user.getId(), "pk-same", order.orderId(), 1000));

        verify(gateway, times(1)).confirm("pk-same", order.orderId(), 1000);
        assertThat(results).allSatisfy(r -> {
            if (r instanceof CustomException e) {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHARGE_IN_PROGRESS);
            }
        });
        assertThat(results.stream().filter(r -> r instanceof CustomException).count()).isLessThan(10);
        assertThat(chaltteokService.balance(user.getId()).balance()).isEqualTo(1000);
        assertThat(ledgerRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(user.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent()).hasSize(1);
        // 끝난 주문을 다시 확인하면 승인을 다시 호출하지 않고 같은 결과를 돌려준다.
        assertThat(chaltteokService.confirmCharge(user.getId(), "pk-same", order.orderId(), 1000).balance())
                .isEqualTo(1000);
        verify(gateway, times(1)).confirm(any(), any(), anyLong());
    }

    @Test
    void declinedPaymentReleasesClaimSoOrderCanBeRetried() {
        UserAccount user = user("c3");
        ChargeResponse order = chaltteokService.createCharge(user.getId(), 1000);
        doThrow(new CustomException(ErrorCode.PAYMENT_DECLINED)).when(gateway).confirm(any(), any(), anyLong());
        assertThatThrownBy(() -> chaltteokService.confirmCharge(user.getId(), "pk-bad", order.orderId(), 1000))
                .isInstanceOf(CustomException.class);
        assertThat(chaltteokService.balance(user.getId()).balance()).isZero();

        reset(gateway);
        assertThat(chaltteokService.confirmCharge(user.getId(), "pk-good", order.orderId(), 1000).balance())
                .isEqualTo(1000);
    }

    @Test
    void parallelPurchasesWithSameIdempotencyKeyDebitOnce() throws Exception {
        UserAccount user = user("c4");
        fund(user, 1000);
        List<Object> results = runParallel(10, () -> chaltteokService.purchase(
                user.getId(), List.of(new PurchaseItem(ProductCode.GEM, null, 20)), "idem-key-0001"));
        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(PurchaseResponse.class));
        assertThat(results.stream().map(r -> ((PurchaseResponse) r).orderId()).distinct()).hasSize(1);
        assertThat(chaltteokService.balance(user.getId()).balance()).isEqualTo(900);
        assertThat(chaltteokService.isConsistent(user.getId())).isTrue();
    }

    @Test
    void firstTouchWalletCreationRaceCreatesExactlyOneWallet() throws Exception {
        UserAccount user = user("c5");
        List<Object> results = runParallel(10, () -> chaltteokService.balance(user.getId()));
        assertThat(results).allSatisfy(r -> assertThat(r).isNotInstanceOf(Exception.class));
        assertThat(results.stream().map(Object::toString).distinct()).hasSize(1);
        assertThat(walletRepository.findAll().stream()
                .filter(w -> w.getUser().getId().equals(user.getId()))).hasSize(1);
    }

    @Test
    void parallelChargeOrderCreationRespectsPendingCap() throws Exception {
        UserAccount user = user("c6");
        List<Object> results = runParallel(25, () -> chaltteokService.createCharge(user.getId(), 300));
        assertThat(results.stream().filter(r -> r instanceof ChargeResponse).count())
                .isEqualTo(ChaltteokService.MAX_PENDING_CHARGES);
    }
}
