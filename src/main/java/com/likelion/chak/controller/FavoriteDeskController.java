package com.likelion.chak.controller;

import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.CommunityDtos.FavoriteDeskResponse;
import com.likelion.chak.service.FavoriteDeskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 친구 책상 즐겨찾기. 로그인한 사용자만 쓴다. */
@RestController
@RequestMapping("/api/users/me/favorite-desks")
@RequiredArgsConstructor
public class FavoriteDeskController {

    private final FavoriteDeskService favoriteDeskService;

    @Operation(summary = "즐겨찾는 책상 목록(최근에 담은 순)", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<List<FavoriteDeskResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(favoriteDeskService.list(user.userId()));
    }

    @Operation(summary = "책상 즐겨찾기에 담기(이미 담았으면 그대로)", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{supporterToken}")
    public ResponseEntity<FavoriteDeskResponse> add(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable("supporterToken") String supporterToken) {
        return ResponseEntity.ok(favoriteDeskService.add(user.userId(), supporterToken));
    }

    @Operation(summary = "책상 즐겨찾기 풀기(담지 않았어도 성공)", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{supporterToken}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable("supporterToken") String supporterToken) {
        favoriteDeskService.remove(user.userId(), supporterToken);
        return ResponseEntity.noContent().build();
    }
}
