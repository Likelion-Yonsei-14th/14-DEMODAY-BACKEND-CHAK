package com.likelion.chak.service;

import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.ReadModeType;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.PublicDeskResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.PersonalDeskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class DeskService {

    private final PersonalDeskRepository personalDeskRepository;

    @Transactional
    public PersonalDesk createClaimedDesk(
            UserAccount owner,
            String displayName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        validateReadMode(readModeType, dailyUnlockTime, capsuleUnlockAt);

        PersonalDesk desk = PersonalDesk.createClaimed(
                owner,
                displayName,
                readModeType,
                dailyUnlockTime,
                capsuleUnlockAt);

        return personalDeskRepository.save(desk);
    }

    @Transactional
    public PersonalDesk createUnclaimedDesk(
            UserAccount creator,
            String displayName,
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        validateReadMode(readModeType, dailyUnlockTime, capsuleUnlockAt);

        PersonalDesk desk = PersonalDesk.createUnclaimed(
                creator,
                displayName,
                readModeType,
                dailyUnlockTime,
                capsuleUnlockAt);

        return personalDeskRepository.save(desk);
    }

    @Transactional(readOnly = true)
    public PublicDeskResponse getPublicDesk(String supporterToken) {
        return PublicDeskResponse.from(findBySupporterToken(supporterToken));
    }

    @Transactional(readOnly = true)
    public PersonalDesk findBySupporterToken(String supporterToken) {
        return personalDeskRepository.findBySupporterToken(supporterToken)
                .orElseThrow(() -> new CustomException(ErrorCode.DESK_NOT_FOUND));
    }

    private void validateReadMode(
            ReadModeType readModeType,
            LocalTime dailyUnlockTime,
            Instant capsuleUnlockAt) {
        if (readModeType == ReadModeType.DAILY && dailyUnlockTime == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (readModeType == ReadModeType.TIME_CAPSULE && capsuleUnlockAt == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
