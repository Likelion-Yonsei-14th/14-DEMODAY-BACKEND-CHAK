package com.likelion.chak.controller;

import com.likelion.chak.alert.PublicWriteLimiter;
import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.ReactionType;
import com.likelion.chak.dto.CommunityDtos.ReactionRequest;
import com.likelion.chak.dto.CommunityDtos.ReactionResponse;
import com.likelion.chak.dto.CommunityDtos.ReplyResponse;
import com.likelion.chak.dto.CommunityDtos.ReportRequest;
import com.likelion.chak.dto.CommunityDtos.SentMessageResponse;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.dto.SliceResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.service.CallerIdentity;
import com.likelion.chak.service.CommunityService;
import com.likelion.chak.service.GuestCookieService;
import com.likelion.chak.service.IdentityService;
import com.likelion.chak.service.ReplyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 편지를 보낸 사람·읽는 사람이 쓰는 API. 로그인하지 않아도 되며 비회원은 쿠키로 식별한다. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCommunityController {

    private final IdentityService identityService;
    private final GuestCookieService guestCookieService;
    private final CommunityService communityService;
    private final ReplyService replyService;
    private final PublicWriteLimiter writeLimiter;

    @GetMapping("/messages/mine")
    public ResponseEntity<SliceResponse<SentMessageResponse>> sentMessages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        CallerIdentity caller = identityService.resolve(principal, guestKey);
        return ResponseEntity.ok(communityService.getSentMessages(caller, page, size));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponseBody> deleteMine(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("messageId") Long messageId) {
        communityService.deleteOwnMessage(identityService.requireIdentity(principal, guestKey), messageId);
        return ResponseEntity.ok(new MessageResponseBody("편지가 삭제되었습니다."));
    }

    @GetMapping("/messages/reactions")
    public ResponseEntity<Map<Long, ReactionType>> myReactions(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @RequestParam(name = "ids") List<Long> ids) {
        if (ids.size() > 200) {
            throw new com.likelion.chak.exception.CustomException(com.likelion.chak.exception.ErrorCode.INVALID_REQUEST);
        }
        CallerIdentity caller = identityService.resolve(principal, guestKey);
        return ResponseEntity.ok(communityService.myReactions(caller, ids).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @PutMapping("/messages/{messageId}/reaction")
    public ResponseEntity<ReactionResponse> react(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("messageId") Long messageId,
            @Valid @RequestBody ReactionRequest request) {
        Writer writer = writer(principal, guestKey);
        ReactionResponse response = communityService.react(writer.identity, messageId, request.type());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        GuestCookieService.addHeader(builder, writer.cookie);
        return builder.body(response);
    }

    @DeleteMapping("/messages/{messageId}/reaction")
    public ResponseEntity<MessageResponseBody> removeReaction(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("messageId") Long messageId) {
        communityService.removeReaction(identityService.resolve(principal, guestKey), messageId);
        return ResponseEntity.ok(new MessageResponseBody("반응을 취소했습니다."));
    }

    @PostMapping("/messages/{messageId}/report")
    public ResponseEntity<MessageResponseBody> report(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("messageId") Long messageId,
            @Valid @RequestBody(required = false) ReportRequest request,
            HttpServletRequest http) {
        String who = principal != null ? "u" + principal.userId() : "ip" + ClientIp.of(http);
        if (!writeLimiter.tryAcquire(PublicWriteLimiter.Action.REPORT, who)) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
        Writer writer = writer(principal, guestKey);
        communityService.report(writer.identity, messageId, request == null ? null : request.reason());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        GuestCookieService.addHeader(builder, writer.cookie);
        return builder.body(new MessageResponseBody("신고를 접수했습니다."));
    }

    @GetMapping("/replies/mine")
    public ResponseEntity<List<ReplyResponse>> repliesToMe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey) {
        return ResponseEntity.ok(replyService.listForAuthor(identityService.resolve(principal, guestKey)));
    }

    /** 쓰기 요청: 비회원 쿠키가 없으면 새로 만들어 함께 내려준다. */
    private Writer writer(AuthenticatedUser principal, String guestKey) {
        if (principal != null) {
            return new Writer(identityService.resolve(principal, null), null);
        }
        GuestSession session = guestCookieService.resolveOrCreate(guestKey);
        return new Writer(new CallerIdentity(null, session.getGuestIdentity()),
                guestCookieService.cookieHeaderIfCreated(session));
    }

    private record Writer(CallerIdentity identity, String cookie) {
    }
}
