package com.likelion.chak.controller;

import com.likelion.chak.alert.MinigameRateLimiter;
import com.likelion.chak.alert.MinigameRateLimiter.Action;
import com.likelion.chak.config.AuthenticatedUser;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.D1CourseReceived;
import com.likelion.chak.dto.MinigameDtos.D1CourseRequest;
import com.likelion.chak.dto.MinigameDtos.Leaderboard;
import com.likelion.chak.dto.MinigameDtos.RoundCreated;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneBoard;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneResult;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneRoundCreated;
import com.likelion.chak.dto.MinigameDtos.SneakyPhoneSubmit;
import com.likelion.chak.dto.MinigameDtos.StudyTypeStats;
import com.likelion.chak.dto.MinigameDtos.StudyTypeSubmit;
import com.likelion.chak.dto.MinigameDtos.SubmitRequest;
import com.likelion.chak.dto.MinigameDtos.SubmitResult;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.service.AuthService;
import com.likelion.chak.service.D1CourseService;
import com.likelion.chak.service.GuessGodService;
import com.likelion.chak.service.SneakyPhoneService;
import com.likelion.chak.service.StudyTypeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미니게임 서버 연동. 로그인 없이 호출할 수 있고, 로그인 토큰을 같이 보내면 회원으로 기록되어 랭킹에 오른다.
 */
@RestController
@RequestMapping("/api/public/minigames")
@RequiredArgsConstructor
public class MinigameController {

    private final GuessGodService guessGodService;
    private final SneakyPhoneService sneakyPhoneService;
    private final StudyTypeService studyTypeService;
    private final D1CourseService d1CourseService;
    private final AuthService authService;
    private final MinigameRateLimiter rateLimiter;

    @PostMapping("/guess-god/rounds")
    public ResponseEntity<RoundCreated> createRound(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest http) {
        limit(Action.GUESS_GOD_ROUND, principal, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(guessGodService.createRound());
    }

    @PostMapping("/guess-god/rounds/{roundId}/submit")
    public ResponseEntity<SubmitResult> submit(
            @PathVariable String roundId,
            @Valid @RequestBody SubmitRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(guessGodService.submit(roundId, request.answers(), viewer(principal)));
    }

    @GetMapping("/guess-god/leaderboard")
    public ResponseEntity<Leaderboard> leaderboard(
            @RequestParam(defaultValue = "daily") String period,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (!"daily".equals(period) && !"overall".equals(period)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(guessGodService.leaderboard(period, viewer(principal)));
    }

    @PostMapping("/sneaky-phone/rounds")
    public ResponseEntity<SneakyPhoneRoundCreated> createSneakyPhoneRound(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest http) {
        limit(Action.SNEAKY_PHONE_ROUND, principal, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(sneakyPhoneService.createRound());
    }

    @PostMapping("/sneaky-phone/rounds/{roundId}/submit")
    public ResponseEntity<SneakyPhoneResult> submitSneakyPhone(
            @PathVariable String roundId,
            @Valid @RequestBody SneakyPhoneSubmit request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(sneakyPhoneService.submit(roundId, request, viewer(principal)));
    }

    @GetMapping("/sneaky-phone/leaderboard")
    public ResponseEntity<SneakyPhoneBoard> sneakyPhoneLeaderboard(
            @RequestParam(defaultValue = "daily") String period,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (!"daily".equals(period) && !"overall".equals(period)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(sneakyPhoneService.leaderboard(period, viewer(principal)));
    }

    /** 로그인 없이, 계정과 연결하지 않고 받는다. 새 응답은 201, 같은 응답을 다시 받으면 200. */
    @PostMapping("/d1-course/responses")
    public ResponseEntity<D1CourseReceived> recordD1Course(
            @Valid @RequestBody D1CourseRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(Action.D1_COURSE_RESPONSE, "ip" + ClientIp.of(http))) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
        boolean created = d1CourseService.record(request);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(new D1CourseReceived(true));
    }

    @PostMapping("/study-type/results")
    public ResponseEntity<StudyTypeStats> recordStudyType(
            @Valid @RequestBody StudyTypeSubmit request,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest http) {
        limit(Action.STUDY_TYPE_RESULT, principal, http);
        if (!StudyTypeService.CODES.contains(request.code())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(studyTypeService.record(request.code(), viewer(principal)));
    }

    @GetMapping("/study-type/stats")
    public ResponseEntity<StudyTypeStats> studyTypeStats(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(studyTypeService.stats(viewer(principal)));
    }

    private UserAccount viewer(AuthenticatedUser principal) {
        return principal == null ? null : authService.getUser(principal.userId());
    }

    private void limit(Action action, AuthenticatedUser principal, HttpServletRequest http) {
        String who = principal != null ? "u" + principal.userId() : "ip" + ClientIp.of(http);
        if (!rateLimiter.tryAcquire(action, who)) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
    }
}
