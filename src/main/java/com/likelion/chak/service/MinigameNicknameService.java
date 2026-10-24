package com.likelion.chak.service;

import com.likelion.chak.domain.MinigameProfile;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.MinigameDtos.NicknameResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.MinigameProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 미니게임 랭킹에 보이는 닉네임. 로그인 회원만 정할 수 있다. */
@Service
@RequiredArgsConstructor
public class MinigameNicknameService {

    private final MinigameProfileRepository repository;
    private final AuthService authService;
    private final NicknameFilter nicknameFilter;

    @Transactional(readOnly = true)
    public NicknameResponse get(Long userId) {
        UserAccount user = authService.getUser(userId);
        String nickname = repository.findById(userId).map(MinigameProfile::getNickname).orElse(null);
        return new NicknameResponse(nickname, MinigameRankingService.defaultNickname(user));
    }

    @Transactional
    public NicknameResponse set(Long userId, String requested) {
        UserAccount user = authService.getUser(userId);
        String nickname = requested == null ? "" : requested.strip();
        if (nickname.length() < 2 || nickname.length() > 12) {
            throw new CustomException(ErrorCode.INVALID_NICKNAME);
        }
        if (!nicknameFilter.isAllowed(nickname)) {
            throw new CustomException(ErrorCode.NICKNAME_NOT_ALLOWED);
        }
        MinigameProfile profile = repository.findById(userId).orElse(null);
        if (profile == null) {
            repository.save(new MinigameProfile(userId, nickname));
        } else {
            profile.rename(nickname);
        }
        return new NicknameResponse(nickname, MinigameRankingService.defaultNickname(user));
    }
}
