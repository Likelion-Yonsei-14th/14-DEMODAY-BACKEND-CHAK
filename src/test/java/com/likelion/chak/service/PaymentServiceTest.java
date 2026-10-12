package com.likelion.chak.service;

import com.likelion.chak.domain.AdEventType;
import com.likelion.chak.domain.AdPlacement;
import com.likelion.chak.domain.MessageVisibility;
import com.likelion.chak.domain.OrderStatus;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ProductCode;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UnlockCategory;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.AdvertisementEventRequest;
import com.likelion.chak.dto.AdvertisementRequest;
import com.likelion.chak.dto.AdvertisementResponse;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.GuestMessageCreateRequest;
import com.likelion.chak.dto.PaymentDtos.OrderCreateRequest;
import com.likelion.chak.dto.PaymentDtos.OrderItemRequest;
import com.likelion.chak.dto.PaymentDtos.OrderResponse;
import com.likelion.chak.dto.PaymentDtos.UnlockRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PaymentServiceTest {

    @Autowired private PaymentService paymentService;
    @Autowired private UnlockService unlockService;
    @Autowired private AdvertisementService advertisementService;
    @Autowired private DeskService deskService;
    @Autowired private MessageService messageService;
    @Autowired private GuestIdentityService guestIdentityService;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ObjectMapper objectMapper;

    private CallerIdentity guest() {
        return new CallerIdentity(null, guestIdentityService.resolveOrCreate(null).getGuestIdentity());
    }

    private PersonalDesk desk(String kakaoId) {
        UserAccount owner = userAccountRepository.save(UserAccount.createKakao(kakaoId, "주인", null, null));
        deskService.createMyDesk(owner, new DeskCreateRequest(
                "책상", ReadModeType.TIME_CAPSULE, null, Instant.parse("2026-01-01T00:00:00Z"),
                true, MessageVisibility.PUBLIC));
        return deskService.findByOwnerId(owner.getId());
    }

    private OrderResponse paidOrder(CallerIdentity payer, OrderItemRequest... items) {
        OrderResponse order = paymentService.createOrder(payer, new OrderCreateRequest(List.of(items)));
        return paymentService.confirm(payer, order.orderId());
    }

    private GuestMessageCreateRequest premiumLetter(Long orderId) {
        return objectMapper.readValue("""
                {"nickname": "친구", "kind": "CARD", "visibility": "PUBLIC", "schemaVersion": 1,
                 "card": {"pages": [{"id": "p1"}]},
                 "paymentOrderId": %s,
                 "object": {"representationType": "CHARM", "x": 50, "y": 50, "rotation": 0, "scale": 1, "zIndex": 1,
                            "metadata": {"material": "acrylic", "gems": [{"id": "a"}, {"id": "b"}, {"id": "c"}]}}}
                """.formatted(orderId), GuestMessageCreateRequest.class);
    }

    @Test
    void serverComputesTheTotalFromItsOwnPriceList() {
        CallerIdentity payer = guest();
        OrderResponse order = paymentService.createOrder(payer, new OrderCreateRequest(List.of(
                new OrderItemRequest(ProductCode.CHARM_ACRYLIC, null, null),
                new OrderItemRequest(ProductCode.GEM, null, 3),
                new OrderItemRequest(ProductCode.GEM, null, 2))));

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.totalAmount()).isEqualTo(50 + 5 * 5);
        assertThat(order.items()).hasSize(2);
        assertThat(paymentService.confirm(payer, order.orderId()).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void anotherPersonCannotSeeOrConfirmMyOrder() {
        OrderResponse order = paymentService.createOrder(guest(),
                new OrderCreateRequest(List.of(new OrderItemRequest(ProductCode.GEM, null, 1))));

        assertThatThrownBy(() -> paymentService.confirm(guest(), order.orderId()))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void premiumLetterNeedsAPaidOrderThatIsUsedOnlyOnce() {
        PersonalDesk desk = desk("payment-premium");
        CallerIdentity friend = guest();

        assertThatThrownBy(() -> messageService.createGuestMessage(
                desk.getSupporterToken(), friend.guest(), premiumLetter(null)))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_REQUIRED);

        OrderResponse tooSmall = paidOrder(friend, new OrderItemRequest(ProductCode.CHARM_ACRYLIC, null, 1));
        assertThatThrownBy(() -> messageService.createGuestMessage(
                desk.getSupporterToken(), friend.guest(), premiumLetter(tooSmall.orderId())))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_REQUIRED);

        OrderResponse full = paidOrder(friend,
                new OrderItemRequest(ProductCode.CHARM_ACRYLIC, null, 1),
                new OrderItemRequest(ProductCode.GEM, null, 3));
        messageService.createGuestMessage(desk.getSupporterToken(), friend.guest(), premiumLetter(full.orderId()));
        assertThat(paymentService.get(friend, full.orderId()).consumed()).isTrue();

        assertThatThrownBy(() -> messageService.createGuestMessage(
                desk.getSupporterToken(), friend.guest(), premiumLetter(full.orderId())))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_USED);
    }

    @Test
    void unpaidOrderDoesNotCoverPremiumItems() {
        PersonalDesk desk = desk("payment-unpaid");
        CallerIdentity friend = guest();
        OrderResponse created = paymentService.createOrder(friend, new OrderCreateRequest(List.of(
                new OrderItemRequest(ProductCode.CHARM_ACRYLIC, null, 1),
                new OrderItemRequest(ProductCode.GEM, null, 3))));

        assertThatThrownBy(() -> messageService.createGuestMessage(
                desk.getSupporterToken(), friend.guest(), premiumLetter(created.orderId())))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_REQUIRED);
    }

    @Test
    void unlockWithoutAdInventoryIsGrantedAndIdempotent() {
        CallerIdentity friend = guest();
        var request = new UnlockRequest(UnlockCategory.STATIONERY, "bg-lace", null);

        unlockService.unlock(friend, request);
        unlockService.unlock(friend, request);

        assertThat(unlockService.list(friend)).singleElement()
                .satisfies(u -> assertThat(u.assetId()).isEqualTo("bg-lace"));
        assertThat(unlockService.list(guest())).isEmpty();
    }

    @Test
    void unlockRequiresACompletedAdViewWhenAdsAreAvailable() {
        AdvertisementResponse ad = advertisementService.create(new AdvertisementRequest(
                "해제 광고", "https://cdn.example.com/a.webp", "https://example.com",
                AdPlacement.UNLOCK, Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z"), true));
        CallerIdentity friend = guest();
        var request = new UnlockRequest(UnlockCategory.DECOR, "clip-01", "ad-session-1");

        assertThatThrownBy(() -> unlockService.unlock(friend, request))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_COMPLETED);

        advertisementService.recordEvent(ad.id(), null, friend.guest(),
                new AdvertisementEventRequest(AdEventType.VIEW_COMPLETED, "ad-session-1", 5000L));
        assertThat(unlockService.unlock(friend, request).assetId()).isEqualTo("clip-01");

        // 다른 사람의 시청 기록으로는 해제할 수 없다.
        assertThatThrownBy(() -> unlockService.unlock(guest(), new UnlockRequest(UnlockCategory.DECOR, "clip-02", "ad-session-1")))
                .extracting(e -> ((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_COMPLETED);
    }
}
