package com.likelion.chak.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 광고 전체 노출 스위치. 꺼져 있으면 등록·예약된 광고가 있어도 사용자에게는 하나도 내보내지 않는다.
 * 설정 줄이 없으면 꺼진 것으로 본다.
 */
@Service
@RequiredArgsConstructor
public class AdSwitchService {

    static final String KEY = "ads.enabled";

    private final JdbcTemplate jdbc;

    public record State(boolean enabled, Instant updatedAt) {
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return state().enabled();
    }

    @Transactional(readOnly = true)
    public State state() {
        List<State> rows = jdbc.query(
                "SELECT setting_value, updated_at FROM app_settings WHERE setting_key = ?",
                (rs, i) -> new State(
                        "true".equalsIgnoreCase(rs.getString(1)),
                        rs.getTimestamp(2).toLocalDateTime().toInstant(ZoneOffset.UTC)),
                KEY);
        return rows.isEmpty() ? new State(false, null) : rows.get(0);
    }

    @Transactional
    public State set(boolean enabled, Long adminId) {
        Instant now = Instant.now();
        Timestamp at = Timestamp.valueOf(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        int updated = jdbc.update(
                "UPDATE app_settings SET setting_value = ?, updated_at = ?, updated_by = ? WHERE setting_key = ?",
                Boolean.toString(enabled), at, adminId, KEY);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO app_settings (setting_key, setting_value, updated_at, updated_by) VALUES (?, ?, ?, ?)",
                    KEY, Boolean.toString(enabled), at, adminId);
        }
        return new State(enabled, now);
    }
}
