# CHAK API 명세

## 1. 공통 규칙

- Base URL: 환경별 서버 주소 + `/api`
- Content-Type: `application/json`
- 시간 형식: ISO 8601 UTC (`2026-11-12T15:00:00Z`)
- 인증 헤더: `Authorization: Bearer {accessToken}`
- 액세스 토큰 유효기간: 기본 30분
- 리프레시 토큰 유효기간: 기본 30일, 재발급할 때마다 회전
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

### 공통 에러 응답

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 값이 올바르지 않습니다."
}
```

## 2. 카카오 로그인 흐름

1. 프론트가 사용자를 카카오 인가 URL로 이동시킵니다.
2. 카카오가 등록된 `redirect_uri`로 `code`를 전달합니다.
3. 프론트가 `code`와 인가 요청 때 사용한 동일한 `redirectUri`를 `POST /api/auth/kakao`로 보냅니다.
4. 백엔드가 카카오 토큰 및 사용자 정보를 조회하고 CHAK 토큰을 반환합니다.
5. 프론트는 보호 API 호출 시 액세스 토큰을 Bearer 헤더에 넣습니다.
6. 액세스 토큰 만료 시 `POST /api/auth/refresh`로 토큰 쌍을 교체합니다.

카카오 인가 URL 형식:

```text
https://kauth.kakao.com/oauth/authorize
  ?client_id={KAKAO_REST_API_KEY}
  &redirect_uri={URL_ENCODED_REDIRECT_URI}
  &response_type=code
  &state={RANDOM_CSRF_VALUE}
```

`redirect_uri`는 카카오 디벨로퍼스에 미리 등록해야 하며, 인가 URL과 로그인 API 요청에서 완전히 같아야 합니다. 프론트는 임의의 `state`를 생성하고 콜백에서 같은 값인지 검증해야 합니다.

## 3. 인증 API

### 3.1 카카오 로그인

`POST /api/auth/kakao`

인증: 불필요

요청:

```json
{
  "code": "kakao-authorization-code",
  "redirectUri": "http://localhost:5173/auth/kakao/callback"
}
```

응답 `200 OK`:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": {
    "id": 1,
    "displayName": "민준",
    "email": "user@example.com",
    "profileImageUrl": "https://..."
  }
}
```

동일한 카카오 회원번호로 다시 로그인하면 새 회원을 만들지 않고 이름, 이메일, 프로필 이미지를 최신 카카오 정보로 갱신합니다. 이메일과 이미지는 사용자가 동의하지 않았다면 `null`입니다.

주요 에러:

| 상태 | 코드 | 의미 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | `code` 또는 `redirectUri` 누락 |
| 401 | `INVALID_KAKAO_AUTH_CODE` | 만료되었거나 잘못된 인가 코드 |
| 502 | `KAKAO_API_ERROR` | 카카오 API 통신 또는 서버 설정 오류 |

### 3.2 토큰 재발급

`POST /api/auth/refresh`

인증: 불필요

요청:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

응답 `200 OK`: 카카오 로그인 응답과 같은 새 토큰 쌍

재발급에 사용한 기존 리프레시 토큰은 즉시 폐기됩니다. 프론트는 응답받은 액세스 토큰과 리프레시 토큰을 모두 교체해야 합니다.

주요 에러:

| 상태 | 코드 | 의미 |
| --- | --- | --- |
| 401 | `INVALID_REFRESH_TOKEN` | 존재하지 않거나 이미 사용·폐기된 토큰 |
| 401 | `EXPIRED_REFRESH_TOKEN` | 만료된 토큰 |

### 3.3 로그아웃

`POST /api/auth/logout`

인증: 필수

요청:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

응답 `200 OK`:

```json
{
  "message": "로그아웃되었습니다."
}
```

서버는 전달된 리프레시 토큰을 폐기합니다. 액세스 토큰은 짧은 만료시간까지 유효하므로 프론트에서도 두 토큰을 즉시 삭제해야 합니다.

### 3.4 내 정보 조회

`GET /api/users/me`

인증: 필수

응답 `200 OK`:

```json
{
  "id": 1,
  "displayName": "민준",
  "email": "user@example.com",
  "profileImageUrl": "https://..."
}
```

## 4. 공개 책상 API

### 4.1 공개 책상 조회

`GET /api/public/desks/{supporterToken}`

인증: 불필요

응답 `200 OK`:

```json
{
  "id": 1,
  "displayName": "지수의 응원 책상",
  "readModeType": "DAILY",
  "dailyUnlockTime": "22:00:00",
  "capsuleUnlockAt": null,
  "publicFeedEnabled": true,
  "roomClosed": false
}
```

### 4.2 공개 편지 목록 조회

`GET /api/public/desks/{supporterToken}/messages`

인증: 불필요

공개 시각이 지났고 `visibility=PUBLIC`이며 삭제되지 않은 편지만 최신순으로 반환합니다. 책상 주인이 공개 피드를 끄면 `403 PUBLIC_FEED_DISABLED`입니다.

응답 `200 OK`:

```json
[
  {
    "id": 10,
    "authorDisplayName": "민준",
    "kind": "CARD",
    "visibility": "PUBLIC",
    "schemaVersion": 1,
    "card": {
      "pages": [
        {"id": "page-1", "elements": []}
      ]
    },
    "unlockAt": "2026-11-12T13:00:00Z",
    "createdAt": "2026-11-11T04:00:00Z"
  }
]
```

### 4.3 편지 작성

`POST /api/public/desks/{supporterToken}/messages`

인증: 선택

- Bearer 토큰 없음: 비회원 편지로 저장하며 `nickname` 필수
- Bearer 토큰 있음: 회원 편지로 저장하며 `nickname` 선택
- 로그인 회원이 `nickname`을 비우면 계정의 현재 `displayName` 사용
- 로그인 회원이 `nickname`을 보내면 해당 편지에만 별칭으로 저장
- 비회원에게는 `chak_guest_id` HttpOnly 쿠키를 발급하며 이후 요청에서 재사용

요청:

```json
{
  "nickname": "민준",
  "kind": "CARD",
  "visibility": "PUBLIC",
  "schemaVersion": 1,
  "card": {
    "pages": [
      {"id": "page-1", "elements": []}
    ]
  },
  "object": {
    "representationType": "LETTER",
    "assetId": "letter-basic",
    "color": "cream",
    "x": 50,
    "y": 50,
    "rotation": 0,
    "scale": 1,
    "zIndex": 1,
    "metadata": {}
  }
}
```

응답: `201 Created`, 본문은 편지 목록 항목과 동일

검증 규칙:

- `schemaVersion`: 현재 `1`만 허용
- `CARD`: `card.pages` 1~3개
- `STICKER`: 카드 본문 생략 가능
- `x`, `y`: 0~100
- `scale`: 0 초과 3 이하
- `visibility`: `PUBLIC` 또는 `PRIVATE`
- `representationType`: `MEMO`, `PHOTO_CARD`, `CHARM`, `POSTER_CARD`, `LETTER`, `TICKET`, `GENERIC_CARD`, `STICKER`

브라우저에서 비회원 API를 호출할 때는 쿠키 유지를 위해 `credentials: "include"`를 사용합니다.

```javascript
await fetch(`${API_URL}/api/public/desks/${supporterToken}/messages`, {
  method: "POST",
  credentials: "include",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(payload),
});
```

## 5. 전체 에러 코드

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 요청 JSON 또는 필수 값 오류 |
| 400 | `GUEST_NICKNAME_REQUIRED` | 비회원 닉네임 누락 또는 길이 초과 |
| 400 | `INVALID_MESSAGE_KIND` | 지원하지 않는 응원 종류 |
| 400 | `INVALID_MESSAGE_VISIBILITY` | 공개 범위 누락 또는 오류 |
| 400 | `INVALID_CARD_PAYLOAD` | 카드 버전, 페이지 수 또는 데이터 오류 |
| 400 | `INVALID_DESK_OBJECT` | 책상 오브젝트 값 오류 |
| 401 | `UNAUTHORIZED` | 액세스 토큰 누락, 만료 또는 변조 |
| 401 | `INVALID_KAKAO_AUTH_CODE` | 카카오 인가 코드 오류 |
| 401 | `INVALID_REFRESH_TOKEN` | 리프레시 토큰 오류 |
| 401 | `EXPIRED_REFRESH_TOKEN` | 리프레시 토큰 만료 |
| 403 | `FORBIDDEN` | 접근 권한 없음 |
| 403 | `PUBLIC_FEED_DISABLED` | 공개 편지 목록 비활성화 |
| 404 | `USER_NOT_FOUND` | 사용자 없음 |
| 404 | `DESK_NOT_FOUND` | 책상 없음 |
| 409 | `DESK_CLOSED` | 편지 접수 종료 |
| 502 | `KAKAO_API_ERROR` | 카카오 API 통신 또는 설정 오류 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

## 6. 아직 제공하지 않는 API

- 개인 책상 생성·수정·종료 및 소유자 편지 관리
- 이미지 업로드
- 광고 조회·시청 집계와 사용자 트래픽 분석
- 교실 단위 응원

위 항목은 URL과 응답 계약이 확정되지 않았으므로 현재 명세에 가짜 엔드포인트를 추가하지 않습니다.
