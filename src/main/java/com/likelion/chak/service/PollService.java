package com.likelion.chak.service;

import com.likelion.chak.dto.PollDtos.AdminPollRequest;
import com.likelion.chak.dto.PollDtos.AdminPollSummary;
import com.likelion.chak.dto.PollDtos.AdminPollUpdate;
import com.likelion.chak.dto.PollDtos.OptionView;
import com.likelion.chak.dto.PollDtos.PollList;
import com.likelion.chak.dto.PollDtos.PollView;
import com.likelion.chak.dto.PollDtos.RegionResult;
import com.likelion.chak.dto.PollDtos.Results;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 수능 여론: 로그인 없이 하루 몇 개씩 여는 짧은 투표. 한 사람(회원 번호 또는 손님 쿠키)이 질문마다 한 번 고르고,
 * 고른 뒤에야 결과(전국·지역별)를 본다. 닫힌 질문은 누구나 결과를 본다.
 * 결과 집계는 수능 당일 저녁처럼 몰릴 때 DB를 지키려고 질문마다 몇 초씩 기억해 둔다.
 */
@Service
@RequiredArgsConstructor
public class PollService {

    /** 이 날짜 수 안에 열릴 질문까지 미리 보여 준다(투표는 열린 뒤에). */
    static final Duration UPCOMING_WINDOW = Duration.ofDays(3);
    /** 닫힌 지 이만큼 지난 질문은 목록에서 뺀다. */
    static final Duration CLOSED_WINDOW = Duration.ofDays(10);
    /** 지역별 결과는 표가 이만큼 모인 지역만 보여 준다(몇 표로 비율이 튀는 것을 막는다). */
    static final int MIN_REGION_VOTES = 10;
    static final Duration RESULT_CACHE = Duration.ofSeconds(5);
    private static final int MAX_LISTED = 40;

    private final JdbcTemplate jdbc;
    private final Map<Long, CachedResults> resultCache = new ConcurrentHashMap<>();

    private record CachedResults(Instant at, Results results) {
    }

    private record PollRow(long id, String slug, String tag, String question, Instant opensAt, Instant closesAt,
                           int sortOrder, boolean hidden) {
        String status(Instant now) {
            if (now.isBefore(opensAt)) return "upcoming";
            if (closesAt != null && !now.isBefore(closesAt)) return "closed";
            return "open";
        }
    }

    private record MyVote(long optionId, String region) {
    }

    // ---- 공개 ----

    @Transactional(readOnly = true)
    public PollList list(String voterKey, Instant now) {
        List<PollRow> polls = jdbc.query("""
                        SELECT id, slug, tag, question, opens_at, closes_at, sort_order, hidden FROM polls
                        WHERE hidden = FALSE AND opens_at <= ? AND (closes_at IS NULL OR closes_at > ?)
                        ORDER BY opens_at DESC, sort_order ASC, id ASC
                        """,
                (rs, i) -> pollRow(rs), utc(now.plus(UPCOMING_WINDOW)), utc(now.minus(CLOSED_WINDOW)));
        if (polls.size() > MAX_LISTED) {
            polls = polls.subList(0, MAX_LISTED);
        }
        Map<Long, List<OptionView>> options = options(polls.stream().map(PollRow::id).toList());
        Map<Long, MyVote> mine = myVotes(voterKey, polls.stream().map(PollRow::id).toList());
        List<PollView> views = new ArrayList<>();
        for (PollRow poll : polls) {
            views.add(view(poll, options.getOrDefault(poll.id(), List.of()), mine.get(poll.id()), now));
        }
        return new PollList(now, views);
    }

    /** 한 표를 넣는다. 이미 고른 질문이면 처음 고른 그대로 두고 지금 결과를 돌려준다. */
    @Transactional
    public PollView vote(long pollId, long optionId, String voterKey, String region, Instant now) {
        PollRow poll = find(pollId);
        if (poll.hidden() || !"open".equals(poll.status(now))) {
            throw new CustomException(ErrorCode.POLL_NOT_OPEN);
        }
        List<OptionView> options = options(List.of(pollId)).getOrDefault(pollId, List.of());
        if (options.stream().noneMatch(o -> o.id() == optionId)) {
            throw new CustomException(ErrorCode.INVALID_POLL_OPTION);
        }
        MyVote existing = myVotes(voterKey, List.of(pollId)).get(pollId);
        if (existing != null) {
            return view(poll, options, existing, now);
        }
        try {
            jdbc.update("INSERT INTO poll_votes (poll_id, option_id, voter_key, region, created_at) VALUES (?, ?, ?, ?, ?)",
                    pollId, optionId, voterKey, region, utc(now));
        } catch (DuplicateKeyException e) {
            // 같은 사람이 거의 동시에 두 번 눌렀다: 먼저 들어간 표를 그대로 둔다.
            MyVote first = myVotes(voterKey, List.of(pollId)).get(pollId);
            return view(poll, options, first, now);
        }
        MyVote mine = new MyVote(optionId, region);
        return withOwnVote(view(poll, options, mine, now), options, mine, now);
    }

    // ---- 운영 콘솔 ----

    @Transactional(readOnly = true)
    public List<AdminPollSummary> adminList(Instant now) {
        List<PollRow> polls = jdbc.query("""
                SELECT id, slug, tag, question, opens_at, closes_at, sort_order, hidden FROM polls
                ORDER BY opens_at DESC, sort_order ASC, id ASC
                """, (rs, i) -> pollRow(rs));
        Map<Long, List<OptionView>> options = options(polls.stream().map(PollRow::id).toList());
        return polls.stream().map(p -> {
            List<OptionView> opts = options.getOrDefault(p.id(), List.of());
            return new AdminPollSummary(p.id(), p.slug(), p.tag(), p.question(), p.opensAt(), p.closesAt(), p.sortOrder(),
                    p.hidden(), p.status(now), opts, computeResults(p.id(), opts));
        }).toList();
    }

    @Transactional
    public AdminPollSummary create(AdminPollRequest request, Instant now) {
        String slug = request.slug().strip();
        List<String> labels = request.options().stream().map(String::strip).toList();
        boolean ok = slug.matches("^[a-z0-9-]{1,60}$")
                && !request.question().isBlank() && !request.tag().isBlank()
                && labels.stream().noneMatch(String::isEmpty)
                && labels.stream().distinct().count() == labels.size()
                && (request.closesAt() == null || request.closesAt().isAfter(request.opensAt()));
        if (!ok) {
            throw new CustomException(ErrorCode.INVALID_POLL);
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO polls (slug, tag, question, opens_at, closes_at, sort_order, hidden, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, FALSE, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, slug);
                ps.setString(2, request.tag().strip());
                ps.setString(3, request.question().strip());
                ps.setObject(4, utc(request.opensAt()));
                ps.setObject(5, request.closesAt() == null ? null : utc(request.closesAt()));
                ps.setInt(6, request.sortOrder() == null ? 0 : request.sortOrder());
                ps.setObject(7, utc(now));
                return ps;
            }, keys);
        } catch (DuplicateKeyException e) {
            throw new CustomException(ErrorCode.INVALID_POLL);
        }
        long pollId = keys.getKeyList().get(0).values().stream()
                .filter(Number.class::isInstance).map(v -> ((Number) v).longValue()).findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
        for (int i = 0; i < labels.size(); i++) {
            jdbc.update("INSERT INTO poll_options (poll_id, label, sort_order) VALUES (?, ?, ?)", pollId, labels.get(i), i + 1);
        }
        return summary(pollId, now);
    }

    @Transactional
    public AdminPollSummary update(long pollId, AdminPollUpdate request, Instant now) {
        PollRow poll = find(pollId);
        Instant opensAt = request.opensAt() == null ? poll.opensAt() : request.opensAt();
        Instant closesAt = Boolean.TRUE.equals(request.clearClosesAt()) ? null
                : request.closesAt() == null ? poll.closesAt() : request.closesAt();
        if (closesAt != null && !closesAt.isAfter(opensAt)) {
            throw new CustomException(ErrorCode.INVALID_POLL);
        }
        boolean hidden = request.hidden() == null ? poll.hidden() : request.hidden();
        jdbc.update("UPDATE polls SET opens_at = ?, closes_at = ?, hidden = ? WHERE id = ?",
                utc(opensAt), closesAt == null ? null : utc(closesAt), hidden, pollId);
        return summary(pollId, now);
    }

    private AdminPollSummary summary(long pollId, Instant now) {
        PollRow p = find(pollId);
        List<OptionView> opts = options(List.of(pollId)).getOrDefault(pollId, List.of());
        return new AdminPollSummary(p.id(), p.slug(), p.tag(), p.question(), p.opensAt(), p.closesAt(), p.sortOrder(),
                p.hidden(), p.status(now), opts, computeResults(pollId, opts));
    }

    // ---- 내부 ----

    private PollView view(PollRow poll, List<OptionView> options, MyVote mine, Instant now) {
        String status = poll.status(now);
        Results results = mine != null || "closed".equals(status) ? cachedResults(poll.id(), options, now) : null;
        return new PollView(poll.id(), poll.slug(), poll.tag(), poll.question(), poll.opensAt(), poll.closesAt(), status,
                options, mine == null ? null : mine.optionId(), mine == null ? null : mine.region(), results);
    }

    /** 방금 넣은 표가 기억해 둔 결과에 아직 없으면 더해서 보여 준다(투표한 사람이 자기 표를 바로 보게). */
    private PollView withOwnVote(PollView view, List<OptionView> options, MyVote mine, Instant votedAt) {
        CachedResults cached = resultCache.get(view.id());
        if (view.results() == null || cached == null || !cached.at().isBefore(votedAt)) {
            return view;
        }
        Results r = view.results();
        List<Long> counts = new ArrayList<>(r.counts());
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id() == mine.optionId()) {
                counts.set(i, counts.get(i) + 1);
            }
        }
        Results adjusted = new Results(r.total() + 1, counts, r.regions());
        return new PollView(view.id(), view.slug(), view.tag(), view.question(), view.opensAt(), view.closesAt(),
                view.status(), view.options(), view.myOptionId(), view.myRegion(), adjusted);
    }

    private Results cachedResults(long pollId, List<OptionView> options, Instant now) {
        CachedResults cached = resultCache.get(pollId);
        if (cached != null && Duration.between(cached.at(), now).compareTo(RESULT_CACHE) < 0 && !now.isBefore(cached.at())) {
            return cached.results();
        }
        Results fresh = computeResults(pollId, options);
        resultCache.put(pollId, new CachedResults(now, fresh));
        return fresh;
    }

    private Results computeResults(long pollId, List<OptionView> options) {
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < options.size(); i++) {
            index.put(options.get(i).id(), i);
        }
        long[] totals = new long[options.size()];
        Map<String, long[]> byRegion = new LinkedHashMap<>();
        jdbc.query("SELECT option_id, region, COUNT(*) AS n FROM poll_votes WHERE poll_id = ? GROUP BY option_id, region",
                rs -> {
                    Integer i = index.get(rs.getLong("option_id"));
                    if (i == null) return;
                    long n = rs.getLong("n");
                    totals[i] += n;
                    String region = rs.getString("region");
                    if (region != null) {
                        byRegion.computeIfAbsent(region, k -> new long[options.size()])[i] += n;
                    }
                }, pollId);
        long total = 0;
        for (long t : totals) total += t;
        List<RegionResult> regions = byRegion.entrySet().stream()
                .map(e -> {
                    long sum = 0;
                    for (long c : e.getValue()) sum += c;
                    return new RegionResult(e.getKey(), sum, toList(e.getValue()));
                })
                .filter(r -> r.total() >= MIN_REGION_VOTES)
                .sorted(Comparator.comparingLong(RegionResult::total).reversed().thenComparing(RegionResult::region))
                .toList();
        return new Results(total, toList(totals), regions);
    }

    private static List<Long> toList(long[] values) {
        List<Long> list = new ArrayList<>(values.length);
        for (long v : values) list.add(v);
        return list;
    }

    private PollRow find(long pollId) {
        return jdbc.query("SELECT id, slug, tag, question, opens_at, closes_at, sort_order, hidden FROM polls WHERE id = ?",
                        (rs, i) -> pollRow(rs), pollId)
                .stream().findFirst().orElseThrow(() -> new CustomException(ErrorCode.POLL_NOT_FOUND));
    }

    private Map<Long, List<OptionView>> options(List<Long> pollIds) {
        if (pollIds.isEmpty()) return Map.of();
        String in = pollIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, List<OptionView>> map = new HashMap<>();
        jdbc.query("SELECT id, poll_id, label FROM poll_options WHERE poll_id IN (" + in + ") ORDER BY poll_id, sort_order, id",
                rs -> {
                    map.computeIfAbsent(rs.getLong("poll_id"), k -> new ArrayList<>())
                            .add(new OptionView(rs.getLong("id"), rs.getString("label")));
                }, pollIds.toArray());
        return map;
    }

    private Map<Long, MyVote> myVotes(String voterKey, List<Long> pollIds) {
        if (voterKey == null || pollIds.isEmpty()) return Map.of();
        String in = pollIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(voterKey);
        args.addAll(pollIds);
        Map<Long, MyVote> map = new HashMap<>();
        jdbc.query("SELECT poll_id, option_id, region FROM poll_votes WHERE voter_key = ? AND poll_id IN (" + in + ")",
                rs -> {
                    map.put(rs.getLong("poll_id"), new MyVote(rs.getLong("option_id"), rs.getString("region")));
                }, args.toArray());
        return map;
    }

    /** DB에는 UTC 벽시계 시각으로 넣는다(JPA 설정 hibernate.jdbc.time_zone=UTC와 같게). */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static PollRow pollRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDateTime closes = rs.getObject("closes_at", LocalDateTime.class);
        return new PollRow(rs.getLong("id"), rs.getString("slug"), rs.getString("tag"), rs.getString("question"),
                rs.getObject("opens_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), closes == null ? null : closes.toInstant(ZoneOffset.UTC),
                rs.getInt("sort_order"), rs.getBoolean("hidden"));
    }
}
