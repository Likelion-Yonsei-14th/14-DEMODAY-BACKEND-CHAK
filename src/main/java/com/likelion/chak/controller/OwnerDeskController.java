package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.BasketRequest;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.DeskSettingsRequest;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.dto.OwnerDeskResponse;
import com.likelion.chak.dto.OwnerMessageResponse;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.DeskService;
import com.likelion.chak.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/desks/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OwnerDeskController {

    private final AuthService authService;
    private final DeskService deskService;
    private final MessageService messageService;

    @Operation(summary = "내 개인 책상 생성")
    @PostMapping
    public ResponseEntity<OwnerDeskResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeskCreateRequest request) {
        UserAccount user = authService.getUser(principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(deskService.createMyDesk(user, request));
    }

    @Operation(summary = "내 개인 책상 조회")
    @GetMapping
    public ResponseEntity<OwnerDeskResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(deskService.getMyDesk(principal.userId()));
    }

    @Operation(summary = "내 개인 책상 설정 변경")
    @PatchMapping("/settings")
    public ResponseEntity<OwnerDeskResponse> updateSettings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeskSettingsRequest request) {
        return ResponseEntity.ok(deskService.updateMyDesk(principal.userId(), request));
    }

    @Operation(summary = "편지 접수 종료")
    @PostMapping("/close")
    public ResponseEntity<OwnerDeskResponse> close(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(deskService.setRoomClosed(principal.userId(), true));
    }

    @Operation(summary = "편지 접수 재개")
    @PostMapping("/open")
    public ResponseEntity<OwnerDeskResponse> open(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(deskService.setRoomClosed(principal.userId(), false));
    }

    @Operation(summary = "받은 편지 목록 조회")
    @GetMapping("/messages")
    public ResponseEntity<SliceResponse<OwnerMessageResponse>> getMessages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "includeLocked", defaultValue = "true") boolean includeLocked,
            @RequestParam(name = "basketed", required = false) Boolean basketed,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(messageService.getOwnerMessages(
                principal.userId(), includeLocked, basketed, page, size));
    }

    @Operation(summary = "읽은 편지를 바구니로 이동")
    @PostMapping("/messages/basket")
    public ResponseEntity<List<OwnerMessageResponse>> basket(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BasketRequest request) {
        return ResponseEntity.ok(messageService.basketOwnerMessages(
                principal.userId(), request.deliveryIds()));
    }

    @Operation(summary = "받은 편지 읽음 처리")
    @PatchMapping("/messages/{deliveryId}/read")
    public ResponseEntity<OwnerMessageResponse> markRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("deliveryId") Long deliveryId) {
        return ResponseEntity.ok(messageService.markOwnerMessageRead(principal.userId(), deliveryId));
    }

    @Operation(summary = "받은 편지 삭제")
    @DeleteMapping("/messages/{deliveryId}")
    public ResponseEntity<MessageResponseBody> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("deliveryId") Long deliveryId) {
        messageService.deleteOwnerMessage(principal.userId(), deliveryId);
        return ResponseEntity.ok(new MessageResponseBody("편지가 삭제되었습니다."));
    }
}
