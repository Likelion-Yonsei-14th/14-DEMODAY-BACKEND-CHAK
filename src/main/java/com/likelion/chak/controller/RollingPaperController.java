package com.likelion.chak.controller;

import com.likelion.chak.alert.PublicWriteLimiter;
import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.dto.GuestSession;
import com.likelion.chak.dto.MessageResponseBody;
import com.likelion.chak.dto.RollingPaperDtos.BoardRequest;
import com.likelion.chak.dto.RollingPaperDtos.ClassView;
import com.likelion.chak.dto.RollingPaperDtos.CreateRequest;
import com.likelion.chak.dto.RollingPaperDtos.JoinRequest;
import com.likelion.chak.dto.RollingPaperDtos.MyClassView;
import com.likelion.chak.dto.RollingPaperDtos.NoteRequest;
import com.likelion.chak.dto.RollingPaperDtos.PaperView;
import com.likelion.chak.dto.RollingPaperDtos.UpdateClassRequest;
import com.likelion.chak.dto.RollingPaperDtos.UpdateMeRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.service.CallerIdentity;
import com.likelion.chak.service.GuestCookieService;
import com.likelion.chak.service.IdentityService;
import com.likelion.chak.service.RollingPaperService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 졸업 롤링페이퍼 API. 로그인하지 않아도 되며 비회원은 쿠키로 식별한다.
 * 반은 초대 링크의 {@code code}로 찾는다.
 */
@RestController
@RequestMapping("/api/public/rolling-papers")
@RequiredArgsConstructor
public class RollingPaperController {

    private final IdentityService identityService;
    private final GuestCookieService guestCookieService;
    private final RollingPaperService rollingPaperService;
    private final PublicWriteLimiter writeLimiter;

    /** 반 만들기. 만든 사람의 롤링페이퍼가 함께 걸린다. */
    @PostMapping
    public ResponseEntity<ClassView> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @Valid @RequestBody CreateRequest request,
            HttpServletRequest http) {
        limit(PublicWriteLimiter.Action.ROLLING_PAPER_CREATE, principal, http);
        Writer writer = writer(principal, guestKey);
        ClassView view = rollingPaperService.create(writer.identity, request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        GuestCookieService.addHeader(builder, writer.cookie);
        return builder.body(view);
    }

    /** 내가 들어간 반들, 최근 순서. */
    @GetMapping("/mine")
    public ResponseEntity<List<MyClassView>> mine(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey) {
        return ResponseEntity.ok(rollingPaperService.mine(identityService.resolve(principal, guestKey)));
    }

    /** 반 보기. 반에 들어오지 않은 사람에게는 이름·공개 방식·인원수만 보인다. */
    @GetMapping("/{code}")
    public ResponseEntity<ClassView> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code) {
        return ResponseEntity.ok(rollingPaperService.get(code, identityService.resolve(principal, guestKey)));
    }

    /** 만든 사람만: 반 이름·공개 방식. */
    @PatchMapping("/{code}")
    public ResponseEntity<ClassView> updateClass(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @Valid @RequestBody UpdateClassRequest request,
            HttpServletRequest http) {
        limit(PublicWriteLimiter.Action.ROLLING_PAPER_WRITE, principal, http);
        return ResponseEntity.ok(rollingPaperService.updateClass(code, identityService.resolve(principal, guestKey), request));
    }

    /** 만든 사람만: 반 삭제. 종이·한마디·칠판 글이 모두 지워진다. */
    @DeleteMapping("/{code}")
    public ResponseEntity<MessageResponseBody> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code) {
        rollingPaperService.delete(code, identityService.resolve(principal, guestKey));
        return ResponseEntity.ok(new MessageResponseBody("반을 삭제했습니다."));
    }

    /** 만든 사람만: 롤링페이퍼 종료. 더 이상 쓸 수 없고 모두 자기 종이를 본다. */
    @PostMapping("/{code}/end")
    public ResponseEntity<ClassView> end(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code) {
        return ResponseEntity.ok(rollingPaperService.end(code, identityService.resolve(principal, guestKey)));
    }

    /** 링크로 들어온 친구가 자기 롤링페이퍼를 건다. */
    @PostMapping("/{code}/members")
    public ResponseEntity<ClassView> join(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @Valid @RequestBody JoinRequest request,
            HttpServletRequest http) {
        limit(PublicWriteLimiter.Action.ROLLING_PAPER_WRITE, principal, http);
        Writer writer = writer(principal, guestKey);
        ClassView view = rollingPaperService.join(code, writer.identity, request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        GuestCookieService.addHeader(builder, writer.cookie);
        return builder.body(view);
    }

    /** 내 이름표·내 종이 디자인. */
    @PatchMapping("/{code}/members/me")
    public ResponseEntity<ClassView> updateMe(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @Valid @RequestBody UpdateMeRequest request,
            HttpServletRequest http) {
        limit(PublicWriteLimiter.Action.ROLLING_PAPER_WRITE, principal, http);
        return ResponseEntity.ok(rollingPaperService.updateMe(code, identityService.resolve(principal, guestKey), request));
    }

    /** 종이 한 장. 아직 열리지 않은 내 종이는 글 없이 개수만 온다. */
    @GetMapping("/{code}/papers/{memberId}")
    public ResponseEntity<PaperView> paper(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @PathVariable("memberId") Long memberId) {
        return ResponseEntity.ok(rollingPaperService.paper(code, memberId, identityService.resolve(principal, guestKey)));
    }

    /** 친구 종이에 내 한마디를 쓰거나 고치거나 옮긴다. */
    @PutMapping("/{code}/papers/{memberId}/note")
    public ResponseEntity<PaperView> writeNote(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody NoteRequest request,
            HttpServletRequest http) {
        limit(PublicWriteLimiter.Action.ROLLING_PAPER_WRITE, principal, http);
        return ResponseEntity.ok(rollingPaperService.writeNote(code, memberId,
                identityService.resolve(principal, guestKey), request));
    }

    @DeleteMapping("/{code}/papers/{memberId}/note")
    public ResponseEntity<PaperView> deleteNote(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @PathVariable("memberId") Long memberId) {
        return ResponseEntity.ok(rollingPaperService.deleteNote(code, memberId,
                identityService.resolve(principal, guestKey)));
    }

    /** 칠판에 한 줄 적기(반 친구 누구나). */
    @PostMapping("/{code}/board")
    public ResponseEntity<ClassView> addBoardEntry(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @Valid @RequestBody BoardRequest request,
            HttpServletRequest http) {
        limit(PublicWriteLimiter.Action.ROLLING_PAPER_WRITE, principal, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(rollingPaperService.addBoardEntry(code,
                identityService.resolve(principal, guestKey), request));
    }

    /** 칠판에서 내가 쓴 줄 지우기. */
    @DeleteMapping("/{code}/board/{entryId}")
    public ResponseEntity<ClassView> removeBoardEntry(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = "${chak.guest-cookie.name}", required = false) String guestKey,
            @PathVariable("code") String code,
            @PathVariable("entryId") Long entryId) {
        return ResponseEntity.ok(rollingPaperService.removeBoardEntry(code, entryId,
                identityService.resolve(principal, guestKey)));
    }

    private void limit(PublicWriteLimiter.Action action, AuthenticatedUser principal, HttpServletRequest http) {
        String who = principal != null ? "u" + principal.userId() : "ip" + ClientIp.of(http);
        if (!writeLimiter.tryAcquire(action, who)) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
    }

    /** 반 만들기·들어가기: 비회원 쿠키가 없으면 새로 만들어 함께 내려준다. */
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
