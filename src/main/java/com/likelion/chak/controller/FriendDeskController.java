package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.DeskCreateRequest;
import com.likelion.chak.dto.FriendDeskDtos.ClaimPreviewResponse;
import com.likelion.chak.dto.FriendDeskDtos.ClaimRequest;
import com.likelion.chak.dto.FriendDeskDtos.ClaimResultResponse;
import com.likelion.chak.dto.FriendDeskDtos.FriendDeskListResponse;
import com.likelion.chak.dto.FriendDeskDtos.FriendDeskResponse;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.FriendDeskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FriendDeskController {

    private final AuthService authService;
    private final FriendDeskService friendDeskService;

    @Operation(summary = "친구를 위한 책상 만들기")
    @PostMapping("/desks/for-friend")
    public ResponseEntity<FriendDeskResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeskCreateRequest request) {
        UserAccount user = authService.getUser(principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(friendDeskService.create(user, request));
    }

    @Operation(summary = "내가 만든, 아직 전달하지 않은 친구 책상 목록")
    @GetMapping("/desks/for-friend")
    public ResponseEntity<FriendDeskListResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(friendDeskService.listCreated(principal.userId()));
    }

    @Operation(summary = "책상 삭제(현재 주인만)")
    @DeleteMapping("/desks/{deskId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("deskId") Long deskId) {
        friendDeskService.delete(principal.userId(), deskId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "받은 책상 링크 미리보기")
    @GetMapping("/claims/{claimToken}")
    public ResponseEntity<ClaimPreviewResponse> preview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("claimToken") String claimToken) {
        return ResponseEntity.ok(friendDeskService.preview(claimToken));
    }

    @Operation(summary = "친구가 만들어 준 책상 넘겨받기")
    @PostMapping("/claims/{claimToken}")
    public ResponseEntity<ClaimResultResponse> claim(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("claimToken") String claimToken,
            @RequestBody ClaimRequest request) {
        UserAccount user = authService.getUser(principal.userId());
        return ResponseEntity.ok(friendDeskService.claim(
                user, claimToken, Boolean.TRUE.equals(request.confirmedRecipient())));
    }
}
