package com.likelion.chak.service;

import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class UnlockTimeCalculator {

    public Instant calculate(PersonalDesk desk, Instant createdAt) {
        if (desk.getReadModeType() == ReadModeType.TIME_CAPSULE) {
            if (desk.getCapsuleUnlockAt() == null) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            return createdAt.isAfter(desk.getCapsuleUnlockAt())
                    ? createdAt
                    : desk.getCapsuleUnlockAt();
        }

        if (desk.getDailyUnlockTime() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        ZoneId zoneId = ZoneId.of(desk.getTimezone());
        ZonedDateTime created = createdAt.atZone(zoneId);
        LocalDate createdDate = created.toLocalDate();
        LocalDateTime cutoff = LocalDateTime.of(createdDate, desk.getDailyUnlockTime());
        ZonedDateTime cutoffAtZone = cutoff.atZone(zoneId);

        if (created.isAfter(cutoffAtZone)) {
            cutoffAtZone = cutoffAtZone.plusDays(1);
        }

        return cutoffAtZone.toInstant();
    }
}
