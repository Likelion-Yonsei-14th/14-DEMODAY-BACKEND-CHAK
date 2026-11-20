package com.likelion.chak.dto;

import com.likelion.chak.domain.RollingPaperRevealMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 졸업 롤링페이퍼 요청/응답 모음. */
public final class RollingPaperDtos {

    private RollingPaperDtos() {
    }

    /** 반 만들기: 반 이름·내 이름·내 종이 디자인·공개 방식을 한 번에 정한다. */
    public record CreateRequest(
            @NotBlank @Size(max = 20) String name,
            @NotBlank @Size(max = 12) String displayName,
            @NotBlank @Size(max = 48) String paperDesign,
            @NotNull RollingPaperRevealMode revealMode) {
    }

    /** 링크로 들어온 친구가 자기 롤링페이퍼를 건다. */
    public record JoinRequest(
            @NotBlank @Size(max = 12) String displayName,
            @NotBlank @Size(max = 48) String paperDesign) {
    }

    /** 만든 사람만: 반 이름·공개 방식 변경. 비운 값은 그대로 둔다. */
    public record UpdateClassRequest(
            @Size(max = 20) String name,
            RollingPaperRevealMode revealMode) {
    }

    /** 누구나: 내 이름표·내 종이 디자인 변경. 비운 값은 그대로 둔다. */
    public record UpdateMeRequest(
            @Size(max = 12) String displayName,
            @Size(max = 48) String paperDesign) {
    }

    /** 친구 종이에 한마디. 위치는 종이 너비·높이의 %(글의 가운데). */
    public record NoteRequest(
            @NotBlank @Size(max = 60) String text,
            @NotBlank @Size(max = 16) String pen,
            @NotBlank @Size(max = 16) String stamp,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal x,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal y) {
    }

    public record BoardRequest(@NotBlank @Size(max = 30) String text) {
    }

    public record MeView(Long memberId, String displayName, String paperDesign, boolean creator) {
    }

    /** 반의 한 사람. 남의 종이에 몇 개가 쓰였는지는 알려 주지 않는다(내가 썼는지만). */
    public record MemberView(Long id, String displayName, String paperDesign, boolean creator, boolean writtenByMe) {
    }

    public record BoardView(Long id, String text, String authorName, boolean mine, Instant createdAt) {
    }

    /** 내 종이: 볼 수 있는지와 몇 명이 썼는지. */
    public record MyPaperView(boolean open, long noteCount) {
    }

    /**
     * 반 화면. 반에 들어오지 않은 사람(초대 링크로 처음 온 사람)에게는 이름·공개 방식·인원수만 보이고
     * {@code me}, {@code myPaper}는 null, 목록은 비어 있다.
     */
    public record ClassView(
            String code,
            String name,
            RollingPaperRevealMode revealMode,
            Instant endedAt,
            long memberCount,
            MeView me,
            List<MemberView> members,
            List<BoardView> board,
            MyPaperView myPaper) {
    }

    public record NoteView(
            Long id,
            Long authorMemberId,
            String authorName,
            String text,
            String pen,
            String stamp,
            BigDecimal x,
            BigDecimal y,
            boolean mine,
            Instant createdAt) {
    }

    /** 종이 한 장. 아직 열리지 않은 내 종이는 {@code sealed=true}이고 글 없이 개수만 온다. */
    public record PaperView(MemberView member, boolean sealed, long noteCount, List<NoteView> notes) {
    }

    /** 내가 들어간 반 목록(홈에서 이어 가기용). */
    public record MyClassView(String code, String name, Instant endedAt, boolean creator) {
    }
}
