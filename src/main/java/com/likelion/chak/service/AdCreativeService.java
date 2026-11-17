package com.likelion.chak.service;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/** 운영자가 올린 광고 이미지를 DB에 담아 두고 그대로 내준다. 파일 앞부분으로 형식을 확인하고 SVG는 받지 않는다. */
@Service
@RequiredArgsConstructor
public class AdCreativeService {

    public static final int MAX_BYTES = 1024 * 1024;

    private final JdbcTemplate jdbc;

    public record Creative(String contentType, byte[] data) {
    }

    @Transactional
    public long upload(byte[] data, Long adminId) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw new CustomException(ErrorCode.INVALID_ADVERTISEMENT);
        }
        String contentType = sniff(data);
        if (contentType == null) {
            throw new CustomException(ErrorCode.INVALID_ADVERTISEMENT);
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ad_creatives (content_type, byte_size, data, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, contentType);
            ps.setInt(2, data.length);
            ps.setBytes(3, data);
            ps.setObject(4, adminId);
            ps.setTimestamp(5, now);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Transactional(readOnly = true)
    public Creative get(long id) {
        List<Creative> rows = jdbc.query(
                "SELECT content_type, data FROM ad_creatives WHERE id = ?",
                (rs, i) -> new Creative(rs.getString(1), rs.getBytes(2)),
                id);
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.ADVERTISEMENT_NOT_FOUND);
        }
        return rows.get(0);
    }

    static String sniff(byte[] d) {
        if (startsWith(d, 0, new byte[]{(byte) 0x89, 'P', 'N', 'G'})) {
            return "image/png";
        }
        if (startsWith(d, 0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "image/jpeg";
        }
        if (startsWith(d, 0, "GIF8".getBytes(StandardCharsets.US_ASCII))) {
            return "image/gif";
        }
        if (startsWith(d, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && startsWith(d, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
