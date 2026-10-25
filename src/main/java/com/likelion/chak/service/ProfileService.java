package com.likelion.chak.service;

import com.likelion.chak.alert.PublicWriteLimiter;
import com.likelion.chak.domain.UserAccount;
import com.likelion.chak.domain.UserAvatar;
import com.likelion.chak.dto.UserResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.UserAvatarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 회원이 직접 고치는 이름과 프로필 사진. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    static final int NAME_MAX = 20;
    static final int AVATAR_MAX_BYTES = 150_000;
    private static final Pattern NAME = Pattern.compile("^[0-9A-Za-z가-힣._()\\- ]+$");
    private static final Pattern DATA_URL = Pattern.compile("^data:(image/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$");

    private final AuthService authService;
    private final UserAvatarRepository avatarRepository;
    private final NicknameFilter nicknameFilter;
    private final PublicWriteLimiter limiter;

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return response(authService.getUser(userId));
    }

    @Transactional
    public UserResponse changeName(Long userId, String requested) {
        limit(userId);
        UserAccount user = authService.getUser(userId);
        String name = requested == null ? "" : requested.strip();
        if (name.isEmpty() || name.length() > NAME_MAX || !NAME.matcher(name).matches()) {
            throw new CustomException(ErrorCode.INVALID_DISPLAY_NAME);
        }
        if (!nicknameFilter.isAllowed(name)) {
            throw new CustomException(ErrorCode.NICKNAME_NOT_ALLOWED);
        }
        user.editDisplayName(name);
        return response(user);
    }

    @Transactional
    public UserResponse setAvatar(Long userId, String dataUrl) {
        limit(userId);
        UserAccount user = authService.getUser(userId);
        Matcher match = DATA_URL.matcher(dataUrl == null ? "" : dataUrl.strip());
        if (!match.matches()) {
            throw new CustomException(ErrorCode.INVALID_AVATAR);
        }
        String contentType = match.group(1);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(match.group(2));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_AVATAR);
        }
        if (bytes.length == 0 || bytes.length > AVATAR_MAX_BYTES || !looksLike(contentType, bytes)) {
            throw new CustomException(ErrorCode.INVALID_AVATAR);
        }
        UserAvatar avatar = avatarRepository.findById(userId).orElse(null);
        if (avatar == null) {
            avatarRepository.save(new UserAvatar(userId, contentType, match.group(2)));
        } else {
            avatar.replace(contentType, match.group(2));
        }
        return response(user);
    }

    @Transactional
    public UserResponse removeAvatar(Long userId) {
        UserAccount user = authService.getUser(userId);
        avatarRepository.deleteById(userId);
        return new UserResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getProfileImageUrl(),
                user.isAdmin(), user.getProvider(), null);
    }

    /** 사진 주소(키)로 사진을 읽는다. 키는 난수라서 모르면 찾을 수 없다. */
    @Transactional(readOnly = true)
    public Avatar avatar(String avatarKey) {
        UserAvatar avatar = avatarRepository.findByAvatarKey(avatarKey == null ? "" : avatarKey)
                .orElseThrow(() -> new CustomException(ErrorCode.AVATAR_NOT_FOUND));
        return new Avatar(avatar.getContentType(), Base64.getDecoder().decode(avatar.getImageBase64()));
    }

    public record Avatar(String contentType, byte[] bytes) {
    }

    private UserResponse response(UserAccount user) {
        return UserResponse.from(user, avatarRepository.findKeyByUserId(user.getId()).orElse(null));
    }

    private void limit(Long userId) {
        if (!limiter.tryAcquire(PublicWriteLimiter.Action.PROFILE_UPDATE, "u" + userId)) {
            throw new CustomException(ErrorCode.TOO_MANY_EVENTS);
        }
    }

    /** 선언한 형식과 실제 파일 첫 바이트가 맞는지 본다(다른 파일을 사진으로 속여 올리지 못하게). */
    private static boolean looksLike(String contentType, byte[] b) {
        return switch (contentType) {
            case "image/jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/webp" -> b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}
