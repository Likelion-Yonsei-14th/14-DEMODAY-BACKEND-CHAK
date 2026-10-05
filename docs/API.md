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
    "profileImageUrl": "https://...",
    "admin": false
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
  "profileImageUrl": "https://...",
  "admin": false
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
  "defaultMessageVisibility": "PRIVATE",
  "roomClosed": false
}
```

### 4.2 공개 편지 목록 조회

`GET /api/public/desks/{supporterToken}/messages?page=0&size=20`

인증: 불필요

공개 시각이 지났고 `visibility=PUBLIC`이며 삭제되지 않은 편지만 최신순으로 반환합니다. 책상 주인이 공개 피드를 끄면 `403 PUBLIC_FEED_DISABLED`입니다.

응답 `200 OK`:

```json
{
  "content": [
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
      "object": {
        "representationType": "LETTER",
        "assetId": "letter-basic",
        "color": "cream",
        "x": 50.0,
        "y": 50.0,
        "rotation": 0.0,
        "scale": 1.0,
        "zIndex": 1,
        "metadata": {}
      },
      "unlockAt": "2026-11-12T13:00:00Z",
      "createdAt": "2026-11-11T04:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

`page`는 0부터 시작하고 `size`는 1~100입니다.

### 4.3 편지 작성

`POST /api/public/desks/{supporterToken}/messages`

인증: 선택

- Bearer 토큰 없음: 비회원 편지로 저장하며 `nickname` 필수
- Bearer 토큰 있음: 회원 편지로 저장하며 `nickname` 선택
- 로그인 회원이 `nickname`을 비우면 계정의 현재 `displayName` 사용
- 로그인 회원이 `nickname`을 보내면 해당 편지에만 별칭으로 저장
- `visibility`를 생략하면 책상 주인이 설정한 `defaultMessageVisibility` 적용
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

## 5. 내 책상 및 받은 편지 API

아래 API는 모두 Bearer 인증이 필요합니다. 개인 책상은 계정당 하나만 만들 수 있습니다.

### 5.1 개인 책상 생성

`POST /api/desks/me`

```json
{
  "displayName": "지수의 응원 책상",
  "readModeType": "TIME_CAPSULE",
  "dailyUnlockTime": null,
  "capsuleUnlockAt": "2026-11-13T00:00:00Z",
  "publicFeedEnabled": true,
  "defaultMessageVisibility": "PRIVATE"
}
```

- `DAILY`: `dailyUnlockTime` 필수
- `TIME_CAPSULE`: `capsuleUnlockAt` 필수
- `publicFeedEnabled` 생략 시 `true`
- `defaultMessageVisibility` 생략 시 `PRIVATE`

응답 `201 Created`:

```json
{
  "id": 1,
  "displayName": "지수의 응원 책상",
  "supporterToken": "550e8400-e29b-41d4-a716-446655440000",
  "readModeType": "TIME_CAPSULE",
  "dailyUnlockTime": null,
  "capsuleUnlockAt": "2026-11-13T00:00:00Z",
  "publicFeedEnabled": true,
  "defaultMessageVisibility": "PRIVATE",
  "roomClosed": false,
  "createdAt": "2026-10-04T07:00:00Z",
  "updatedAt": "2026-10-04T07:00:00Z"
}
```

### 5.2 내 책상 조회

`GET /api/desks/me`

응답: 개인 책상 생성 응답과 동일

### 5.3 내 책상 설정 변경

`PATCH /api/desks/me/settings`

```json
{
  "displayName": "새 책상 이름",
  "readModeType": "DAILY",
  "dailyUnlockTime": "22:00:00",
  "capsuleUnlockAt": null,
  "publicFeedEnabled": false,
  "defaultMessageVisibility": "PRIVATE",
  "applyVisibilityToExisting": true
}
```

`applyVisibilityToExisting=true`이면 삭제되지 않은 과거 편지를 `defaultMessageVisibility`로 일괄 변경합니다.

### 5.4 편지 접수 종료·재개

```text
POST /api/desks/me/close
POST /api/desks/me/open
```

### 5.5 받은 편지 조회

`GET /api/desks/me/messages?includeLocked=true&page=0&size=20`

- `includeLocked` 기본값: `true`
- 잠긴 편지도 오브젝트와 작성자 정보는 반환하지만 `card`는 `null`
- 삭제된 편지는 반환하지 않음

```json
{
  "content": [
    {
      "deliveryId": 21,
      "messageId": 10,
      "authorDisplayName": "민준",
      "kind": "CARD",
      "visibility": "PRIVATE",
      "status": "SENT",
      "locked": true,
      "schemaVersion": 1,
      "card": null,
      "object": {
        "representationType": "LETTER",
        "assetId": "letter-basic",
        "color": "cream",
        "x": 50.0,
        "y": 50.0,
        "rotation": 0.0,
        "scale": 1.0,
        "zIndex": 1,
        "metadata": {}
      },
      "unlockAt": "2026-11-13T00:00:00Z",
      "readAt": null,
      "createdAt": "2026-10-04T07:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

`page`는 0부터 시작하고 `size`는 1~100입니다.

### 5.6 읽음 처리·삭제

```text
PATCH  /api/desks/me/messages/{deliveryId}/read
DELETE /api/desks/me/messages/{deliveryId}
```

잠긴 편지는 읽음 처리할 수 없습니다. 삭제는 soft delete입니다.

## 6. 이미지 업로드 API

Bearer 인증이 필요합니다. 지원 형식은 JPEG, PNG, WebP, GIF이며 프로필은 최대 5MB, 카드는 최대 10MB입니다.

### 6.1 업로드 URL 발급

`POST /api/media/presign`

```json
{
  "purpose": "CARD",
  "originalFileName": "photo.webp",
  "contentType": "image/webp",
  "size": 1048576
}
```

응답 `201 Created`:

```json
{
  "assetId": 3,
  "uploadUrl": "https://s3...",
  "method": "PUT",
  "requiredHeaders": {"Content-Type": "image/webp"},
  "expiresAt": "2026-10-04T07:10:00Z"
}
```

프론트는 `uploadUrl`에 원본 파일을 `PUT`하고 응답에 지정된 헤더를 그대로 사용해야 합니다.

### 6.2 업로드 완료 확인

`POST /api/media/{assetId}/complete`

서버가 S3 `HEAD` 요청으로 파일 존재 여부, 크기, MIME 타입을 검증합니다. 검증 후 파일을 비공개 최종 키로 복사하고 임시 객체를 정리합니다. 업로드 URL은 만료 전까지 임시 키를 다시 만들 수 있지만 확정된 최종 파일은 덮어쓸 수 없습니다. 버킷에는 `tmp/` 객체 수명 주기 삭제 정책을 설정해야 합니다.

```json
{
  "id": 3,
  "purpose": "CARD",
  "status": "UPLOADED",
  "contentType": "image/webp",
  "size": 1048576,
  "uploadedAt": "2026-10-04T07:03:00Z"
}
```

### 6.3 비공개 다운로드 URL

`GET /api/media/{assetId}/download-url`

자산 소유자에게만 10분짜리 presigned GET URL을 반환합니다.

S3 버킷은 프론트 도메인의 `PUT` 요청과 `Content-Type` 헤더를 허용하도록 CORS 설정해야 합니다. 서버는 IAM Role 또는 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`로 접근합니다.

## 7. 광고 및 트래픽 API

### 7.1 활성 광고 조회

`GET /api/public/ads?placement=DESK`

`placement`: `HOME`, `DESK`, `MESSAGE_COMPLETE`

현재 시간이 노출 기간 안에 있고 활성화된 광고만 반환합니다.

### 7.2 광고 이벤트 기록

`POST /api/public/ads/{advertisementId}/events`

인증: 선택

```json
{
  "eventType": "VIEW_COMPLETED",
  "sessionId": "ad-exposure-uuid",
  "viewDurationMs": 15000
}
```

`eventType`: `IMPRESSION`, `VIEW_STARTED`, `VIEW_COMPLETED`, `CLICK`

`sessionId`는 광고가 실제로 한 번 렌더링될 때마다 새로 만드는 노출 식별자로 필수입니다. 네트워크 재시도에는 같은 값을 사용하며, 같은 광고·이벤트 종류·`sessionId` 조합은 한 번만 저장합니다. 비활성 또는 노출 기간 밖 광고의 이벤트는 거부하며, 비회원은 `chak_guest_id` 쿠키로 연결됩니다.

### 7.3 트래픽 이벤트 기록

`POST /api/public/analytics/events`

인증: 선택

```json
{
  "eventId": "0199f8be-66a0-7000-8000-000000000001",
  "eventType": "PAGE_VIEW",
  "sessionId": "browser-session-uuid",
  "path": "/desk/550e8400-e29b-41d4-a716-446655440000",
  "referrer": "https://example.com",
  "resourceType": "PERSONAL_DESK",
  "resourceKey": "550e8400-e29b-41d4-a716-446655440000",
  "metadata": {"utmSource": "instagram"}
}
```

`eventId`는 이벤트마다 생성한 고유 값이며 네트워크 재시도에는 같은 값을 사용합니다. 클라이언트가 보낼 수 있는 `eventType`은 `PAGE_VIEW`, `MESSAGE_COMPOSE_STARTED`입니다.

`INVITE_LINK_OPEN`, `MESSAGE_CREATED`는 서버에서만 자동 기록합니다. 광고 이벤트와 트래픽 이벤트는 각각 회원 또는 비회원 식별자별 분당 120건으로 제한하며, 운영 환경에서는 게이트웨이/IP 단위 제한도 함께 설정해야 합니다. IP와 User-Agent 원문은 저장하지 않습니다.

## 8. 운영자 API

Bearer 인증이 필요하며 카카오 회원번호가 `ADMIN_KAKAO_IDS`에 등록된 계정만 접근할 수 있습니다.

```text
GET   /api/admin/ads
POST  /api/admin/ads
PATCH /api/admin/ads/{advertisementId}
DELETE /api/admin/ads/{advertisementId}
GET   /api/admin/ads/{advertisementId}/metrics?from={ISO_INSTANT}&to={ISO_INSTANT}
GET   /api/admin/analytics/summary?from={ISO_INSTANT}&to={ISO_INSTANT}
```

광고 생성·수정 요청:

```json
{
  "title": "응원 캠페인",
  "creativeUrl": "https://cdn.example.com/ad.webp",
  "destinationUrl": "https://example.com/campaign",
  "placement": "DESK",
  "startsAt": "2026-10-04T00:00:00Z",
  "endsAt": "2026-11-30T23:59:59Z",
  "active": true
}
```

`DELETE`는 이력을 유지한 채 광고를 비활성화합니다. 광고별 지표는 해당 광고의 노출·시청·클릭 건수를 반환합니다. 전체 분석 요약은 트래픽과 광고 이벤트 종류별 건수를 반환합니다. 조회 범위는 최대 366일이며 기간을 생략하면 최근 7일입니다.

## 9. 전체 에러 코드

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 요청 JSON 또는 필수 값 오류 |
| 400 | `GUEST_NICKNAME_REQUIRED` | 비회원 닉네임 누락 또는 길이 초과 |
| 400 | `INVALID_MESSAGE_KIND` | 지원하지 않는 응원 종류 |
| 400 | `INVALID_CARD_PAYLOAD` | 카드 버전, 페이지 수 또는 데이터 오류 |
| 400 | `INVALID_DESK_OBJECT` | 책상 오브젝트 값 오류 |
| 400 | `INVALID_DESK_SETTINGS` | 공개 시각 설정 오류 |
| 400 | `INVALID_MEDIA` | 이미지 형식·크기 또는 업로드 결과 오류 |
| 400 | `INVALID_ADVERTISEMENT` | 광고 기간 또는 URL 오류 |
| 400 | `INVALID_ANALYTICS_EVENT` | 분석 이벤트 또는 조회 기간 오류 |
| 401 | `UNAUTHORIZED` | 액세스 토큰 누락, 만료 또는 변조 |
| 401 | `INVALID_KAKAO_AUTH_CODE` | 카카오 인가 코드 오류 |
| 401 | `INVALID_REFRESH_TOKEN` | 리프레시 토큰 오류 |
| 401 | `EXPIRED_REFRESH_TOKEN` | 리프레시 토큰 만료 |
| 403 | `FORBIDDEN` | 접근 권한 없음 |
| 403 | `PUBLIC_FEED_DISABLED` | 공개 편지 목록 비활성화 |
| 403 | `ADMIN_REQUIRED` | 운영자 권한 없음 |
| 404 | `USER_NOT_FOUND` | 사용자 없음 |
| 404 | `DESK_NOT_FOUND` | 책상 없음 |
| 404 | `MESSAGE_NOT_FOUND` | 소유한 편지가 없음 |
| 404 | `MEDIA_NOT_FOUND` | 소유한 미디어 자산이 없음 |
| 404 | `ADVERTISEMENT_NOT_FOUND` | 광고 없음 |
| 409 | `DESK_CLOSED` | 편지 접수 종료 |
| 409 | `DESK_ALREADY_EXISTS` | 계정에 이미 책상이 있음 |
| 409 | `MESSAGE_LOCKED` | 공개 시각 전 읽기 요청 |
| 409 | `MEDIA_UPLOAD_NOT_FOUND` | S3에서 업로드 결과를 찾지 못함 |
| 429 | `TOO_MANY_EVENTS` | 이벤트 요청 한도 초과 |
| 502 | `KAKAO_API_ERROR` | 카카오 API 통신 또는 설정 오류 |
| 502 | `STORAGE_API_ERROR` | Object Storage 통신 오류 |
| 503 | `STORAGE_NOT_CONFIGURED` | Object Storage 환경변수 미설정 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

## 10. 아직 제공하지 않는 API

- 교실 단위 응원
- 이미지 삭제 및 미완료 업로드 정리 배치
- 상세 분석 대시보드용 일별 집계 테이블

교실 기능은 Post-MVP 범위입니다.
