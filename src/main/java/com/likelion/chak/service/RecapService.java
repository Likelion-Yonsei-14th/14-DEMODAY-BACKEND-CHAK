package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameType;
import com.likelion.chak.dto.RecapDtos.GameStat;
import com.likelion.chak.dto.RecapDtos.Recap;
import com.likelion.chak.dto.RecapDtos.Received;
import com.likelion.chak.dto.RecapDtos.Sent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 나의 수능 리캡에 들어갈 숫자를 모은다.
 * 어떤 카드를 보여 줄지(적으면 숨기기 등)는 화면이 정하고, 여기서는 있는 그대로 센다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecapService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;

    public Recap recap(CallerIdentity caller) {
        Long userId = caller.userId();
        Long guestId = caller.guestId();
        Long deskId = userId == null ? null : jdbc.query(
                "select id from personal_desks where owner_id = ? and deleted_at is null",
                rs -> rs.next() ? rs.getLong(1) : null, userId);

        Received received = deskId == null ? null : received(deskId);
        Sent sent = sent(userId, guestId);
        List<GameStat> games = userId == null ? List.of() : games(userId);
        String studyType = userId == null ? null : jdbc.query(
                "select code from study_type_results where user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, userId);

        Instant joined = userId != null
                ? first("select created_at from users where id = ?", userId)
                : guestId != null ? first("select created_at from guest_identities where id = ?", guestId) : null;
        Instant deskCreated = deskId == null ? null : first("select created_at from personal_desks where id = ?", deskId);
        LocalDate firstDay = Stream.of(joined, deskCreated)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(this::day)
                .orElse(null);

        return new Recap(userId != null, firstDay, received, sent, games, studyType);
    }

    private Received received(long deskId) {
        List<Object[]> rows = jdbc.query("""
                        select m.kind, m.author_user_id, m.author_guest_id, m.created_at
                        from personal_message_deliveries d
                        join messages m on m.id = d.message_id
                        where d.desk_id = ? and m.status <> 'DELETED' and m.deleted_at is null""",
                (rs, i) -> new Object[]{
                        rs.getString(1),
                        rs.getObject(2) == null ? null : rs.getLong(2),
                        rs.getObject(3) == null ? null : rs.getLong(3),
                        rs.getTimestamp(4)},
                deskId);
        int letters = 0;
        int stickers = 0;
        java.util.Set<String> senders = new java.util.HashSet<>();
        Map<LocalDate, Integer> perDay = new TreeMap<>();
        for (Object[] row : rows) {
            if ("STICKER".equals(row[0])) stickers++;
            else letters++;
            if (row[1] != null) senders.add("u" + row[1]);
            else if (row[2] != null) senders.add("g" + row[2]);
            perDay.merge(day(((Timestamp) row[3]).toInstant()), 1, Integer::sum);
        }
        LocalDate firstDay = perDay.isEmpty() ? null : perDay.keySet().iterator().next();
        Map.Entry<LocalDate, Integer> peak = perDay.entrySet().stream()
                .max(Map.Entry.<LocalDate, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .orElse(null);
        return new Received(letters, stickers, senders.size(), firstDay,
                peak == null ? null : peak.getKey(), peak == null ? 0 : peak.getValue());
    }

    private Sent sent(Long userId, Long guestId) {
        if (userId == null && guestId == null) return new Sent(0, 0);
        String author = userId != null ? "m.author_user_id = ?" : "m.author_guest_id = ?";
        Object id = userId != null ? userId : guestId;
        return jdbc.queryForObject("""
                        select count(*), count(distinct d.desk_id)
                        from messages m
                        left join personal_message_deliveries d on d.message_id = m.id
                        where %s and m.status <> 'DELETED' and m.deleted_at is null""".formatted(author),
                (rs, i) -> new Sent(rs.getInt(1), rs.getInt(2)), id);
    }

    private List<GameStat> games(long userId) {
        List<GameStat> games = new ArrayList<>();
        for (MinigameType game : MinigameType.values()) {
            int plays = jdbc.queryForObject("""
                    select count(*) from minigame_rounds
                    where game = ? and user_id = ? and submitted_at is not null""",
                    Integer.class, game.name(), userId);
            if (plays == 0) continue;
            Integer best = jdbc.queryForObject(
                    "select max(score) from minigame_rounds where game = ? and user_id = ? and score is not null",
                    Integer.class, game.name(), userId);
            Integer topPercent = null;
            if (best != null) {
                List<Integer> bests = jdbc.queryForList("""
                        select max(score) from minigame_rounds
                        where game = ? and user_id is not null and score is not null
                        group by user_id""", Integer.class, game.name());
                long better = bests.stream().filter(score -> score > best).count();
                // 나보다 잘한 사람 수 + 나 = 순위. 상위 몇 %인지 올림해서 1~100.
                topPercent = (int) Math.max(1, Math.ceil((better + 1) * 100.0 / bests.size()));
            }
            games.add(new GameStat(game.name(), plays, best, topPercent));
        }
        return games;
    }

    private Instant first(String sql, long id) {
        return jdbc.query(sql, rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null, id);
    }

    private LocalDate day(Instant instant) {
        return instant.atZone(SEOUL).toLocalDate();
    }
}
