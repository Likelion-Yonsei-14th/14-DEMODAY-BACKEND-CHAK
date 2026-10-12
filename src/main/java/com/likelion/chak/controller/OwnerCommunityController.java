package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.CommunityDtos.BlockRequest;
import com.likelion.chak.dto.CommunityDtos.BlockResponse;
import com.likelion.chak.dto.CommunityDtos.ReplyCreateRequest;
import com.likelion.chak.dto.CommunityDtos.ReplyResponse;
import com.likelion.chak.dto.CommunityDtos.SupporterResponse;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.service.BlockService;
import com.likelion.chak.service.ReplyService;
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

import java.util.List;

@RestController
@RequestMapping("/api/desks/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OwnerCommunityController {

    private final ReplyService replyService;
    private final BlockService blockService;

    @PostMapping("/replies")
    public ResponseEntity<ReplyResponse> reply(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ReplyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(replyService.create(principal.userId(), request));
    }

    @GetMapping("/replies")
    public ResponseEntity<List<ReplyResponse>> replies(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(replyService.listForOwner(principal.userId()));
    }

    @GetMapping("/supporters")
    public ResponseEntity<List<SupporterResponse>> supporters(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(blockService.supporters(principal.userId()));
    }

    @PostMapping("/blocks")
    public ResponseEntity<BlockResponse> block(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody BlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(blockService.block(principal.userId(), request.deliveryId()));
    }

    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<MessageResponseBody> unblock(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("blockId") Long blockId) {
        blockService.unblock(principal.userId(), blockId);
        return ResponseEntity.ok(new MessageResponseBody("숨김을 해제했습니다."));
    }
}
