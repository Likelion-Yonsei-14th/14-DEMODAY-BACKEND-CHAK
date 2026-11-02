package com.likelion.chak.service;

import com.likelion.chak.domain.FavoriteDesk;
import com.likelion.chak.domain.PersonalDesk;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.dto.CommunityDtos.FavoriteDeskResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.FavoriteDeskRepository;
import com.likelion.chak.repository.PersonalDeskRepository;
import com.likelion.chak.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 로그인한 사용자가 친구 책상을 즐겨찾기한다. 담는 건 책상 링크(supporterToken)로 하며,
 * 같은 책상을 다시 담아도 한 번만 남는다. 한 사람이 담을 수 있는 책상은 {@link #MAX_FAVORITES}개까지다.
 */
@Service
@RequiredArgsConstructor
public class FavoriteDeskService {

    static final int MAX_FAVORITES = 100;

    private final FavoriteDeskRepository favoriteDeskRepository;
    private final PersonalDeskRepository deskRepository;
    private final UserAccountRepository userRepository;

    @Transactional(readOnly = true)
    public List<FavoriteDeskResponse> list(Long userId) {
        return favoriteDeskRepository.findAllLiveByUserId(userId).stream().map(FavoriteDeskService::toResponse).toList();
    }

    @Transactional
    public FavoriteDeskResponse add(Long userId, String supporterToken) {
        PersonalDesk desk = deskRepository.findBySupporterTokenAndDeletedAtIsNull(supporterToken)
                .orElseThrow(() -> new CustomException(ErrorCode.DESK_NOT_FOUND));
        // 같은 사용자의 동시 요청이 한 줄씩 지나가게 해서, 중복 저장과 한도 초과를 막는다.
        UserAccount user = userRepository.findForUpdateById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return favoriteDeskRepository.findByUserIdAndDeskId(userId, desk.getId())
                .map(FavoriteDeskService::toResponse)
                .orElseGet(() -> {
                    if (favoriteDeskRepository.countLiveByUserId(userId) >= MAX_FAVORITES) {
                        throw new CustomException(ErrorCode.FAVORITE_LIMIT_EXCEEDED);
                    }
                    return toResponse(favoriteDeskRepository.save(FavoriteDesk.create(user, desk)));
                });
    }

    /** 없는 책상이나 담지 않은 책상을 풀어도 오류가 아니다. */
    @Transactional
    public void remove(Long userId, String supporterToken) {
        deskRepository.findBySupporterToken(supporterToken)
                .flatMap(desk -> favoriteDeskRepository.findByUserIdAndDeskId(userId, desk.getId()))
                .ifPresent(favoriteDeskRepository::delete);
    }

    private static FavoriteDeskResponse toResponse(FavoriteDesk favorite) {
        PersonalDesk desk = favorite.getDesk();
        return new FavoriteDeskResponse(desk.getSupporterToken(), desk.getDisplayName(), favorite.getCreatedAt());
    }
}
