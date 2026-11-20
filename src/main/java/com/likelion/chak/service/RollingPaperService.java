package com.likelion.chak.service;

import com.likelion.chak.domain.RollingPaperBoardEntry;
import com.likelion.chak.domain.RollingPaperClass;
import com.likelion.chak.domain.RollingPaperMember;
import com.likelion.chak.domain.RollingPaperNote;
import com.likelion.chak.dto.RollingPaperDtos.BoardRequest;
import com.likelion.chak.dto.RollingPaperDtos.BoardView;
import com.likelion.chak.dto.RollingPaperDtos.ClassView;
import com.likelion.chak.dto.RollingPaperDtos.CreateRequest;
import com.likelion.chak.dto.RollingPaperDtos.JoinRequest;
import com.likelion.chak.dto.RollingPaperDtos.MeView;
import com.likelion.chak.dto.RollingPaperDtos.MemberView;
import com.likelion.chak.dto.RollingPaperDtos.MyClassView;
import com.likelion.chak.dto.RollingPaperDtos.MyPaperView;
import com.likelion.chak.dto.RollingPaperDtos.NoteRequest;
import com.likelion.chak.dto.RollingPaperDtos.NoteView;
import com.likelion.chak.dto.RollingPaperDtos.PaperView;
import com.likelion.chak.dto.RollingPaperDtos.UpdateClassRequest;
import com.likelion.chak.dto.RollingPaperDtos.UpdateMeRequest;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.RollingPaperBoardEntryRepository;
import com.likelion.chak.repository.RollingPaperClassRepository;
import com.likelion.chak.repository.RollingPaperMemberRepository;
import com.likelion.chak.repository.RollingPaperNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 졸업 롤링페이퍼. 반을 만든 사람(첫 사물함)과 링크로 들어온 친구가 각자 롤링페이퍼를 걸고
 * 서로의 종이에 한마디씩 남긴다.
 * <ul>
 *   <li>자기 종이에는 쓸 수 없다. 자기 종이는 공개 방식이 {@code INSTANT}이거나 반이 종료된 뒤에만 볼 수 있다.</li>
 *   <li>친구 종이는 반 친구 누구나 읽는다(빈 자리를 고르는 데 쓴다).</li>
 *   <li>종료되면 더 이상 종이에 쓸 수 없다. 종료·반 이름·공개 방식·삭제는 만든 사람만 한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RollingPaperService {

    /** 사물함 칸은 아홉 개지만 "전체 보기"로 더 볼 수 있다. 한 반으로 넉넉한 값. */
    static final int MAX_MEMBERS = 60;
    static final int MAX_BOARD_ENTRIES = 300;

    private static final Set<String> PENS = Set.of("ink", "red", "blue", "green", "purple", "pink");
    private static final Set<String> STAMPS = Set.of("none", "heart", "star", "flower", "smile", "cap");
    /** 프론트의 편지지·기본 배경 id. 목록은 프론트가 정하므로 모양만 확인한다. */
    private static final Pattern DESIGN = Pattern.compile("^bg-[a-z0-9-]{1,45}$");

    private final RollingPaperClassRepository classRepository;
    private final RollingPaperMemberRepository memberRepository;
    private final RollingPaperNoteRepository noteRepository;
    private final RollingPaperBoardEntryRepository boardRepository;

    @Transactional
    public ClassView create(CallerIdentity caller, CreateRequest request) {
        requirePresent(caller);
        RollingPaperClass paperClass = classRepository.save(
                RollingPaperClass.create(text(request.name(), 20), request.revealMode()));
        memberRepository.save(RollingPaperMember.create(paperClass, caller.user(), caller.guest(),
                text(request.displayName(), 12), design(request.paperDesign()), true));
        return view(paperClass, caller);
    }

    @Transactional(readOnly = true)
    public ClassView get(String code, CallerIdentity caller) {
        return view(find(code), caller);
    }

    @Transactional(readOnly = true)
    public List<MyClassView> mine(CallerIdentity caller) {
        if (!caller.isPresent()) {
            return List.of();
        }
        return memberRepository.findAllMine(caller.userId(), caller.guestId()).stream()
                .map(m -> new MyClassView(m.getPaperClass().getCode(), m.getPaperClass().getName(),
                        m.getPaperClass().getEndedAt(), m.isCreator()))
                .toList();
    }

    @Transactional
    public ClassView join(String code, CallerIdentity caller, JoinRequest request) {
        requirePresent(caller);
        RollingPaperClass paperClass = find(code);
        if (me(paperClass, caller) != null) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_ALREADY_JOINED);
        }
        if (memberRepository.countByPaperClassId(paperClass.getId()) >= MAX_MEMBERS) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_FULL);
        }
        try {
            memberRepository.saveAndFlush(RollingPaperMember.create(paperClass, caller.user(), caller.guest(),
                    text(request.displayName(), 12), design(request.paperDesign()), false));
        } catch (DataIntegrityViolationException duplicate) {
            // 같은 사람이 두 번 눌러 동시에 들어온 경우
            throw new CustomException(ErrorCode.ROLLING_PAPER_ALREADY_JOINED);
        }
        return view(paperClass, caller);
    }

    @Transactional
    public ClassView updateClass(String code, CallerIdentity caller, UpdateClassRequest request) {
        RollingPaperClass paperClass = find(code);
        requireCreator(paperClass, caller);
        if (request.name() != null && !request.name().isBlank()) {
            paperClass.rename(text(request.name(), 20));
        }
        if (request.revealMode() != null) {
            paperClass.changeRevealMode(request.revealMode());
        }
        return view(paperClass, caller);
    }

    @Transactional
    public ClassView updateMe(String code, CallerIdentity caller, UpdateMeRequest request) {
        RollingPaperClass paperClass = find(code);
        RollingPaperMember me = requireMember(paperClass, caller);
        if (request.displayName() != null && !request.displayName().isBlank()) {
            me.rename(text(request.displayName(), 12));
        }
        if (request.paperDesign() != null && !request.paperDesign().isBlank()) {
            me.changeDesign(design(request.paperDesign()));
        }
        return view(paperClass, caller);
    }

    /** 종료: 더 이상 종이에 쓸 수 없고, 모두 자기 종이를 본다. 되돌릴 수 없다. */
    @Transactional
    public ClassView end(String code, CallerIdentity caller) {
        RollingPaperClass paperClass = find(code);
        requireCreator(paperClass, caller);
        paperClass.end(Instant.now());
        return view(paperClass, caller);
    }

    /** 반 삭제: 종이·한마디·칠판 글을 모두 지운다. */
    @Transactional
    public void delete(String code, CallerIdentity caller) {
        RollingPaperClass paperClass = find(code);
        requireCreator(paperClass, caller);
        Long classId = paperClass.getId();
        noteRepository.deleteAllByClassId(classId);
        boardRepository.deleteAllByClassId(classId);
        memberRepository.deleteAllByClassId(classId);
        classRepository.deleteByIdNow(classId);
    }

    @Transactional(readOnly = true)
    public PaperView paper(String code, Long memberId, CallerIdentity caller) {
        RollingPaperClass paperClass = find(code);
        RollingPaperMember me = requireMember(paperClass, caller);
        RollingPaperMember owner = memberRepository.findByIdAndPaperClassId(memberId, paperClass.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROLLING_PAPER_MEMBER_NOT_FOUND));
        return paperView(paperClass, owner, me);
    }

    /** 친구 종이에 한마디를 쓰거나 고치거나 자리를 옮긴다. */
    @Transactional
    public PaperView writeNote(String code, Long memberId, CallerIdentity caller, NoteRequest request) {
        RollingPaperClass paperClass = find(code);
        RollingPaperMember me = requireMember(paperClass, caller);
        RollingPaperMember owner = writableOwner(paperClass, memberId, me);
        String noteText = text(request.text(), 60);
        String pen = allowed(request.pen(), PENS);
        String stamp = allowed(request.stamp(), STAMPS);
        BigDecimal x = percent(request.x());
        BigDecimal y = percent(request.y());
        noteRepository.findByPaperIdAndAuthorId(owner.getId(), me.getId()).ifPresentOrElse(
                note -> note.write(noteText, pen, stamp, x, y),
                () -> noteRepository.save(RollingPaperNote.create(owner, me, noteText, pen, stamp, x, y)));
        return paperView(paperClass, owner, me);
    }

    @Transactional
    public PaperView deleteNote(String code, Long memberId, CallerIdentity caller) {
        RollingPaperClass paperClass = find(code);
        RollingPaperMember me = requireMember(paperClass, caller);
        RollingPaperMember owner = writableOwner(paperClass, memberId, me);
        noteRepository.findByPaperIdAndAuthorId(owner.getId(), me.getId()).ifPresent(noteRepository::delete);
        return paperView(paperClass, owner, me);
    }

    @Transactional
    public ClassView addBoardEntry(String code, CallerIdentity caller, BoardRequest request) {
        RollingPaperClass paperClass = find(code);
        RollingPaperMember me = requireMember(paperClass, caller);
        if (boardRepository.countByPaperClassId(paperClass.getId()) >= MAX_BOARD_ENTRIES) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_FULL);
        }
        boardRepository.save(RollingPaperBoardEntry.create(me, text(request.text(), 30)));
        return view(paperClass, caller);
    }

    /** 칠판에서는 내가 쓴 줄만 지운다. */
    @Transactional
    public ClassView removeBoardEntry(String code, Long entryId, CallerIdentity caller) {
        RollingPaperClass paperClass = find(code);
        RollingPaperMember me = requireMember(paperClass, caller);
        RollingPaperBoardEntry entry = boardRepository.findByIdAndPaperClassId(entryId, paperClass.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        if (!entry.getAuthor().getId().equals(me.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        boardRepository.delete(entry);
        return view(paperClass, caller);
    }

    // ---- 내부 ----

    private ClassView view(RollingPaperClass paperClass, CallerIdentity caller) {
        Long classId = paperClass.getId();
        RollingPaperMember me = me(paperClass, caller);
        long memberCount = memberRepository.countByPaperClassId(classId);
        if (me == null) {
            // 초대 링크로 처음 온 사람: 이름과 공개 방식만
            return new ClassView(paperClass.getCode(), paperClass.getName(), paperClass.getRevealMode(),
                    paperClass.getEndedAt(), memberCount, null, List.of(), List.of(), null);
        }
        Set<Long> written = new HashSet<>(noteRepository.findPaperIdsWrittenBy(me.getId()));
        List<RollingPaperMember> members = memberRepository.findAllByPaperClassIdOrderByIdAsc(classId);
        Map<Long, String> names = members.stream()
                .collect(Collectors.toMap(RollingPaperMember::getId, RollingPaperMember::getDisplayName));
        List<MemberView> memberViews = members.stream()
                .map(m -> memberView(m, written.contains(m.getId())))
                .toList();
        List<BoardView> board = boardRepository.findAllByPaperClassIdOrderByIdAsc(classId).stream()
                .map(b -> new BoardView(b.getId(), b.getText(), names.getOrDefault(b.getAuthor().getId(), ""),
                        b.getAuthor().getId().equals(me.getId()), b.getCreatedAt()))
                .toList();
        return new ClassView(paperClass.getCode(), paperClass.getName(), paperClass.getRevealMode(),
                paperClass.getEndedAt(), memberCount,
                new MeView(me.getId(), me.getDisplayName(), me.getPaperDesign(), me.isCreator()),
                memberViews, board,
                new MyPaperView(paperClass.isOwnPaperOpen(), noteRepository.countByPaperId(me.getId())));
    }

    private PaperView paperView(RollingPaperClass paperClass, RollingPaperMember owner, RollingPaperMember me) {
        boolean mine = owner.getId().equals(me.getId());
        boolean written = !mine && noteRepository.findByPaperIdAndAuthorId(owner.getId(), me.getId()).isPresent();
        MemberView member = memberView(owner, written);
        if (mine && !paperClass.isOwnPaperOpen()) {
            // 아직 열리지 않은 내 종이: 누가 뭐라고 썼는지는 비밀, 개수만
            return new PaperView(member, true, noteRepository.countByPaperId(owner.getId()), List.of());
        }
        List<RollingPaperNote> notes = noteRepository.findAllByPaperIdOrderByIdAsc(owner.getId());
        Map<Long, RollingPaperMember> authors = memberRepository
                .findAllByPaperClassIdOrderByIdAsc(paperClass.getId()).stream()
                .collect(Collectors.toMap(RollingPaperMember::getId, Function.identity()));
        List<NoteView> noteViews = notes.stream()
                .map(n -> {
                    Long authorId = n.getAuthor().getId();
                    RollingPaperMember author = authors.get(authorId);
                    return new NoteView(n.getId(), authorId, author == null ? "" : author.getDisplayName(),
                            n.getText(), n.getPen(), n.getStamp(), n.getX(), n.getY(),
                            authorId.equals(me.getId()), n.getCreatedAt());
                })
                .toList();
        return new PaperView(member, false, noteViews.size(), noteViews);
    }

    private static MemberView memberView(RollingPaperMember member, boolean writtenByMe) {
        return new MemberView(member.getId(), member.getDisplayName(), member.getPaperDesign(),
                member.isCreator(), writtenByMe);
    }

    private RollingPaperClass find(String code) {
        if (code == null || code.isBlank() || code.length() > 32) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_NOT_FOUND);
        }
        return classRepository.findByCode(code)
                .orElseThrow(() -> new CustomException(ErrorCode.ROLLING_PAPER_NOT_FOUND));
    }

    private RollingPaperMember me(RollingPaperClass paperClass, CallerIdentity caller) {
        if (caller.user() != null) {
            return memberRepository.findByPaperClassIdAndUserId(paperClass.getId(), caller.userId()).orElse(null);
        }
        if (caller.guest() != null) {
            return memberRepository.findByPaperClassIdAndGuestId(paperClass.getId(), caller.guestId()).orElse(null);
        }
        return null;
    }

    private RollingPaperMember requireMember(RollingPaperClass paperClass, CallerIdentity caller) {
        RollingPaperMember me = me(paperClass, caller);
        if (me == null) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_NOT_MEMBER);
        }
        return me;
    }

    private void requireCreator(RollingPaperClass paperClass, CallerIdentity caller) {
        if (!requireMember(paperClass, caller).isCreator()) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_CREATOR_ONLY);
        }
    }

    /** 쓸 수 있는 친구 종이: 같은 반의 남의 종이이고, 반이 아직 종료되지 않았다. */
    private RollingPaperMember writableOwner(RollingPaperClass paperClass, Long memberId, RollingPaperMember me) {
        if (paperClass.isEnded()) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_ENDED);
        }
        RollingPaperMember owner = memberRepository.findByIdAndPaperClassId(memberId, paperClass.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.ROLLING_PAPER_MEMBER_NOT_FOUND));
        if (owner.getId().equals(me.getId())) {
            throw new CustomException(ErrorCode.ROLLING_PAPER_OWN_PAPER);
        }
        return owner;
    }

    private static void requirePresent(CallerIdentity caller) {
        if (!caller.isPresent()) {
            throw new CustomException(ErrorCode.IDENTITY_REQUIRED);
        }
    }

    private static String text(String value, int max) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > max) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return trimmed;
    }

    private static String allowed(String value, Set<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private static String design(String value) {
        if (value == null || !DESIGN.matcher(value).matches()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private static BigDecimal percent(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
