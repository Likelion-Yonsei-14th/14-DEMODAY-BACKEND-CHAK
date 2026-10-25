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

### 인증 필요 여부 한눈에 보기

| 경로 | 인증 |
| --- | --- |
| `POST /api/auth/kakao`, `POST /api/auth/google`, `POST /api/auth/refresh` | 불필요 |
| `/api/public/**` | 선택 (Bearer가 있으면 회원, 없으면 `chak_guest_id` 쿠키의 비회원으로 처리) |
| `POST /api/auth/logout`, `/api/users/**`, `/api/desks/me/**`, `/api/desks/for-friend`, `/api/desks/{id}`(삭제), `/api/claims/**`, `/api/media/**` | 필수 |
| `/api/admin/**` | 필수 + 운영자 |

> 주의: `/api/public/**`도 `Authorization` 헤더가 **있으면** 토큰을 검증합니다. 만료·변조된 토큰을 보내면 공개 API라도 `401 UNAUTHORIZED`가 반환되므로, 프론트는 401을 받으면 `/api/auth/refresh` 후 재시도하거나 헤더 없이 비회원으로 재요청해야 합니다.

### 프론트 통신 설정

- **CORS**: 서버는 `CORS_ALLOWED_ORIGINS`(쉼표 구분, 기본 `http://localhost:3000,http://localhost:5173`)의 Origin만 허용합니다. 허용 메서드 `GET POST PUT PATCH DELETE OPTIONS`, 허용 헤더 `Authorization`, `Content-Type`, 자격증명(쿠키) 허용. 와일드카드 불가. 배포 도메인은 백엔드 환경변수에 추가해야 합니다.
- **비회원 쿠키 `chak_guest_id`**: HttpOnly, 1년, `Path=/`. 모든 `/api/public/**` 호출에 `credentials: "include"`(axios는 `withCredentials: true`)를 사용해야 합니다. JS에서 읽을 수 없습니다.
- **쿠키 SameSite/Secure**: 기본 `SameSite=Lax`, `Secure=false`. 프론트와 API가 **다른 사이트(도메인)** 이면 쿠키가 전송되지 않으므로 백엔드에 `GUEST_COOKIE_SAME_SITE=None`, `GUEST_COOKIE_SECURE=true`(HTTPS 필수)를 설정합니다. 같은 사이트(서브도메인 포함)면 기본값을 유지합니다. 로컬 개발(`localhost` ↔ `localhost`)은 기본값으로 동작합니다.
- **토큰 저장**:
  - access 토큰은 응답 본문으로 전달되며 **메모리에만 보관**하고 Bearer 헤더로 전송합니다(local/sessionStorage 저장 금지).
  - refresh 토큰은 `chak_refresh_token` **HttpOnly 쿠키**(`Path=/api/auth`)로도 내려갑니다. 쿠키로 받으면 JS가 다룰 필요가 없습니다.
  - 운영(prod)에서는 응답 본문의 `refreshToken`이 `null`입니다(기본 `REFRESH_TOKEN_IN_BODY=false`). 로컬 개발에서만 본문에도 내려가며(`true`), 쿠키만 쓰는 프론트는 본문 값에 의존하지 마세요. XSS가 생겨도 토큰이 새지 않게 하려는 설정입니다.
  - 새로고침으로 메모리의 access 토큰이 사라지면 앱 시작 시 `POST /api/auth/refresh`(본문 없이, `credentials: "include"`)를 호출해 복구합니다. 쿠키도 없으면 `401 INVALID_REFRESH_TOKEN`이며 비로그인 상태로 처리합니다.
- **에러 응답**: 404·405·415를 포함한 모든 에러는 `{ "code", "message" }` 형식이며 필드별 검증 상세는 없습니다. 프론트는 `code`로 분기하세요.
- **JSON 필드 이름**: camelCase. 알 수 없는 필드는 무시, `null`은 허용되는 필드에서만 사용.
- **이미지 업로드**: S3로 직접 `PUT`하므로 S3 버킷 CORS에 프론트 Origin, `PUT` 메서드, `Content-Type` 헤더를 허용해야 합니다(백엔드 CORS와 별개).

### enum 값

| 이름 | 값 |
| --- | --- |
| ReadModeType | `DAILY`, `TIME_CAPSULE` |
| MessageVisibility | `PUBLIC`, `PRIVATE` |
| MessageKind | `CARD`, `STICKER` |
| MessageStatus | `SENT`, `READ` (삭제된 편지는 반환되지 않음) |
| DeskObjectType (`representationType`) | `MEMO`, `PHOTO_CARD`, `CHARM`, `POSTER_CARD`, `LETTER`, `TICKET`, `GENERIC_CARD`, `STICKER` |
| MediaPurpose | `PROFILE`, `CARD` |
| MediaStatus | `PENDING`, `UPLOADED` |
| AdPlacement | `HOME`, `DESK`, `MESSAGE_COMPLETE`, `UNLOCK`(편지지·소품 해제용) |
| AdEventType | `IMPRESSION`, `VIEW_STARTED`, `VIEW_COMPLETED`, `CLICK` |
| ReactionType | `HEART`, `TEARY`, `CLAP` |
| ReplyScope | `SINGLE`(이 편지만), `DAILY`(오늘 받은 편지 한 번에) |
| ReportStatus | `OPEN`, `RESOLVED`, `DISMISSED` |
| ProductCode | `CHARM_ACRYLIC`, `GEM`, `UNIVERSITY_CHARM`, `PENNANT`, `LOCKER_BULB`, `LOCKER_PAINT` |
| UnlockCategory | `STATIONERY`(편지지), `DECOR`(클립·마스킹 테이프) |
| TrafficEventType (클라이언트 전송 가능) | `PAGE_VIEW`, `MESSAGE_COMPOSE_STARTED` |

잘못된 enum 값이나 JSON 파싱 오류는 `400 INVALID_REQUEST`입니다.

### 시간 규칙

- 모든 시각 필드(`Instant`)는 ISO 8601 UTC 문자열입니다.
- `dailyUnlockTime`은 `HH:mm:ss` 형식의 **한국시간(Asia/Seoul) 기준** 로컬 시각입니다.
- `DAILY` 책상: 작성 시각이 당일 `dailyUnlockTime`을 지났으면 다음 날 `dailyUnlockTime`, 아니면 당일 `dailyUnlockTime`이 `unlockAt`입니다.
- `TIME_CAPSULE` 책상: `unlockAt`은 `capsuleUnlockAt`이며, 이미 지난 뒤 작성한 편지는 작성 시각입니다(즉시 열림).

### DB 구조

테이블·컬럼·제약은 [`DATABASE.md`](DATABASE.md)를 참고하세요.

## 2. 소셜 로그인 흐름(카카오·구글)

1. 프론트가 사용자를 카카오 또는 구글 인가 URL로 이동시킵니다.
2. 제공자가 등록된 `redirect_uri`로 `code`를 전달합니다.
3. 프론트가 `code`와 인가 요청 때 사용한 동일한 `redirectUri`를 `POST /api/auth/kakao` 또는 `POST /api/auth/google`로 보냅니다.
4. 백엔드가 제공자의 토큰 및 사용자 정보를 조회하고 CHAK 토큰을 반환합니다.
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

구글 인가 URL 형식:

```text
https://accounts.google.com/o/oauth2/v2/auth
  ?client_id={GOOGLE_CLIENT_ID}
  &redirect_uri={URL_ENCODED_REDIRECT_URI}
  &response_type=code
  &scope=openid%20email%20profile
  &state={RANDOM_CSRF_VALUE}
```

`redirect_uri`는 카카오 디벨로퍼스·구글 클라우드 콘솔에 미리 등록해야 하며, 인가 URL과 로그인 API 요청에서 완전히 같아야 합니다. 프론트는 임의의 `state`를 생성하고 콜백에서 같은 값인지 검증해야 합니다.

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

응답 `200 OK` (헤더에 `Set-Cookie: chak_refresh_token=...; HttpOnly; Path=/api/auth` 포함):

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

### 3.1.1 구글 로그인

`POST /api/auth/google` — 요청·응답은 3.1과 같습니다(`code`, `redirectUri`). 응답의 `user`에는 어떤 로그인으로 만든 계정인지 `provider`(`KAKAO` | `GOOGLE`)가 함께 옵니다.

- 계정은 **(제공자, 제공자 식별자)** 로만 찾습니다(카카오는 회원번호, 구글은 변하지 않는 `sub`). 같은 사람이 카카오와 구글로 각각 로그인하면 **서로 다른 계정**이며, 이메일이 같아도 자동으로 합치지 않습니다(검증되지 않은 이메일로 남의 계정을 가로채는 것을 막기 위해). 계정 연결은 별도 기능으로 둡니다.
- 구글이 검증한 이메일(`email_verified`)만 저장하며, 아니면 `null`입니다. 이름은 최대 50자로 잘라 저장합니다.
- 코드는 1회용이라 만료·재사용되면 `401 INVALID_GOOGLE_AUTH_CODE`, 서버의 구글 클라이언트 설정 오류나 구글 장애는 `502 GOOGLE_API_ERROR`(서버 로그에 원인 기록)입니다.
- 서버에 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`이 없으면 `502 GOOGLE_API_ERROR`입니다.

### 3.2 토큰 재발급

`POST /api/auth/refresh`

인증: 불필요

요청: **본문 없이 쿠키만 보내는 방식(권장)** 또는 본문으로 토큰 전달. 둘 다 있으면 본문이 우선입니다.

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

- 쿠키 방식은 `credentials: "include"`가 필요합니다.
- 쿠키로 받은 요청에 `Origin` 헤더가 있으면 허용된 프론트 Origin이어야 하며 아니면 `403`입니다(CSRF 방어).
- 본문·쿠키 모두 없으면 `401 INVALID_REFRESH_TOKEN`입니다.

응답 `200 OK`: 카카오 로그인 응답과 같은 새 토큰 쌍(쿠키도 새 값으로 교체됨)

재발급에 사용한 기존 리프레시 토큰은 즉시 폐기됩니다. 프론트는 응답받은 액세스 토큰과 리프레시 토큰을 모두 교체해야 합니다.

주요 에러:

| 상태 | 코드 | 의미 |
| --- | --- | --- |
| 401 | `INVALID_REFRESH_TOKEN` | 존재하지 않거나 이미 사용·폐기된 토큰 |
| 401 | `EXPIRED_REFRESH_TOKEN` | 만료된 토큰 |

### 3.3 로그아웃

`POST /api/auth/logout`

인증: 필수

요청: 재발급과 같이 본문 또는 쿠키로 리프레시 토큰을 전달합니다.

응답 `200 OK` (리프레시 쿠키 삭제 헤더 포함):

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
  "profileImageUrl": "https://...",      // 카카오·구글이 준 사진 주소(바뀌지 않음)
  "admin": false,
  "provider": "GOOGLE",
  "avatarUrl": "/api/public/avatars/…"   // 직접 올린 프로필 사진. 올리지 않았으면 null
}
```

로그인·재발급 응답의 `user`도 같은 모양(`avatarUrl` 포함)입니다.

### 3.5 내 이름·프로필 사진 바꾸기

인증: 필수. 응답은 모두 바뀐 `UserResponse`(3.4와 같은 모양)이고, 변경은 회원 한 명이 시간당 20번까지입니다(넘으면 `429 TOO_MANY_EVENTS`).

| 메서드 | 경로 | 설명 |
|---|---|---|
| PATCH | `/api/users/me` | `{ "displayName": "새 이름" }` — 앞뒤 공백을 지운 1~20자, 한글·영문·숫자·공백·`.`·`_`·`-`·`()`만. 욕설·위험 단어는 `400 NICKNAME_NOT_ALLOWED`, 형식 오류는 `400 INVALID_DISPLAY_NAME` |
| PUT | `/api/users/me/avatar` | `{ "image": "data:image/jpeg;base64,…" }` — JPEG·PNG·WebP, 풀어서 150KB 이하. 실제 파일 첫 바이트가 선언과 다르거나 SVG 등이면 `400 INVALID_AVATAR` |
| DELETE | `/api/users/me/avatar` | 올린 사진을 지움 |
| GET | `/api/public/avatars/{avatarKey}` | 사진 파일. **로그인 없이** 읽을 수 있어 `<img src>`에 쓰고, 주소(키)가 올릴 때마다 새로 만드는 난수라서 아는 사람만 볼 수 있습니다. 없으면 `404 AVATAR_NOT_FOUND`. 1년 캐시(`immutable`) |

- **직접 고친 이름은 다음 로그인 때 카카오·구글 이름으로 덮어쓰지 않습니다.** 이메일과 `profileImageUrl`은 계속 제공자를 따릅니다.
- 사진을 바꾸면 주소도 바뀌므로 브라우저는 새 사진을 받습니다. 회원 탈퇴 처리 때 사진은 함께 삭제됩니다.
- `avatarUrl`은 같은 서버의 상대 경로입니다(프런트는 API 주소를 앞에 붙여 씁니다).

## 4. 공개 책상 API

### 4.0 서버 상태 확인

`GET /api/public/health` → `200 OK` `{ "status": "UP" }`. 인증 불필요이며 프로세스가 떠 있는지만 봅니다(DB와 무관). 프론트 연결 확인과 서버의 자동 교체·롤백 판단에 사용합니다.

`GET /api/public/health/db` → DB에서 `SELECT 1`이 4초 안에 되면 `200 {"status":"UP","db":"UP"}`, 연결이 안 되거나 멈추면 `503 {"status":"DOWN","db":"DOWN"}`(원인은 노출하지 않고 서버 로그에만 남깁니다). **외부 감시(UptimeRobot)는 이쪽을 확인합니다.** 서버는 살아 있는데 DB 연결이 막힌 장애를 잡기 위한 것입니다.

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

공개 시각이 지났고 `visibility=PUBLIC`이며 삭제되지 않은 편지만 최신순으로 반환합니다. 책상 주인이 숨긴 작성자([5.9](#59-숨긴-작성자-관리))의 편지는 제외됩니다. 책상 주인이 공개 피드를 끄면 `403 PUBLIC_FEED_DISABLED`입니다.

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
- `CARD`: `card.pages` 1~3개, 각 페이지는 객체, 카드 전체 JSON 500,000자 이하(**비회원은 100,000자 이하**, 넘으면 `INVALID_CARD_PAYLOAD`)
- **보내는 횟수 제한(429)**: 같은 IP에서 비회원은 시간당 30건, 같은 비회원 쿠키는 시간당 20건, 로그인 회원은 시간당 60건을 넘으면 `TOO_MANY_MESSAGES`입니다. 또 한 책상이 시간당 300건, 하루 1,500건을 받으면 그 책상은 잠시 `DESK_BUSY`로 새 응원을 거절합니다(검증에 실패한 요청은 책상 상한에 세지 않습니다). IP는 헤더로 바꿀 수 있어서, 흩어진 주소로 한 책상에 쏟아지는 스팸은 책상별 상한이 막습니다.
- **비회원은 사진을 첨부할 수 없습니다.** 비회원 요청의 카드 어느 페이지에든 `photoElements`가 비어 있지 않으면 `403 GUEST_IMAGE_NOT_ALLOWED`입니다(빈 배열은 허용). 사진은 로그인한 회원만 첨부할 수 있고, 이미지 업로드 API(6장)도 로그인이 필요합니다.
- 페이지당 `stickerElements` 최대 10개, `photoElements` 최대 10개(배열이어야 하며 초과 시 `INVALID_CARD_PAYLOAD`). 텍스트 요소는 개수 제한 없음(전체 크기 한도만 적용)
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

### 4.4 광고 상담 신청

`POST /api/public/promotion/inquiries` — 브랜드 담당자가 `/promotion/apply`에서 남기는 상담 신청. 로그인 없이 호출합니다.

```json
{ "company": "착커피", "contactName": "김담당", "email": "kim@example.com",
  "phone": "010-0000-0000", "message": "배너만 가능한지 궁금해요", "consent": true, "website": "" }
```

- 필수는 `company`, `contactName`, `email`, `consent: true`입니다. `phone`(숫자·`+-() `만, 30자), `category`, `interest`(`LOCAL_A`/`LOCAL_B`/`LOCAL_C`/`BRAND_S`/`BRAND_M`/`BRAND_L`/`ENTERPRISE`/`JOINT`/`OTHER`, 이전 제안서의 `PACKAGE`/`BANNER`는 기존 신청서 조회용으로만 남김), `message`(2,000자)는 선택입니다.
- 응답은 `201 { "received": true }`입니다.
- **`website`는 사람에게 보이지 않는 칸**입니다. 값이 있으면 자동 입력으로 보고 저장하지 않고 같은 `201`로 답합니다. 같은 회사·이메일의 24시간 안 재전송도 저장하지 않고 `201`로 답합니다.
- 접속자(IP 해시)당 1시간 5건, 전체 1시간 60건을 넘으면 `429 TOO_MANY_EVENTS`입니다.
- 저장하면 운영자 디스코드에 "새 광고 상담 신청"(회사명·업종·관심 구성만, 연락처는 싣지 않음)이 갑니다.
- 담당자 연락처가 들어 있어 **접수 1년 뒤 자동 삭제**합니다(`chak.retention.promotion-inquiry-days`).

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
  "pushEnabled": true,
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
  "applyVisibilityToExisting": true,
  "pushEnabled": true
}
```

- `applyVisibilityToExisting=true`이면 삭제되지 않은 과거 편지를 `defaultMessageVisibility`로 일괄 변경합니다.
- `pushEnabled`는 생략하면 현재 값을 유지합니다. 새 응원 알림 선호만 저장하며 알림 발송 기능은 아직 없습니다.
- **공개 시각 재계산**: 열람 방식(`readModeType`, `dailyUnlockTime`, `capsuleUnlockAt`)을 바꾸면 **아직 열리지 않은 편지**의 `unlockAt`이 새 설정으로 다시 계산됩니다. **이미 열린 편지는 다시 잠기지 않습니다.** 프론트는 편지의 `unlockAt`을 그대로 신뢰하고, 설정 값으로 다시 계산하지 마세요.

### 5.4 편지 접수 종료·재개

```text
POST /api/desks/me/close
POST /api/desks/me/open
```

### 5.5 받은 편지 조회

`GET /api/desks/me/messages?includeLocked=true&basketed=false&page=0&size=20`

- `includeLocked` 기본값: `true`
- `basketed` 생략 시 전체, `true`면 바구니 안, `false`면 바구니 밖 편지만 조회
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
      "basketedAt": null,
      "createdAt": "2026-10-04T07:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

`page`는 0부터 시작하고 `size`는 1~100입니다. `basketedAt`은 바구니로 옮긴 시각이며 바구니 밖이면 `null`입니다.

### 5.6 읽음 처리·삭제

```text
PATCH  /api/desks/me/messages/{deliveryId}/read
DELETE /api/desks/me/messages/{deliveryId}
```

잠긴 편지는 읽음 처리할 수 없습니다. 삭제는 soft delete입니다.

### 5.7 읽은 편지 바구니로 이동

`POST /api/desks/me/messages/basket`

```json
{ "deliveryIds": [21, 22] }
```

- 1~100개, 중복 ID는 무시합니다.
- 모든 편지가 읽음 상태여야 하며 하나라도 읽지 않았으면 `409 MESSAGE_NOT_READ`로 전체가 거부됩니다.
- 이미 바구니에 있는 편지는 그대로 둡니다(멱등).
- 내 책상의 편지가 아니거나 삭제된 편지가 있으면 `404 MESSAGE_NOT_FOUND`입니다.
- 응답 `200 OK`: 처리된 `OwnerMessageResponse` 배열(`basketedAt` 설정됨)
- 바구니에서 꺼내는 API는 아직 없습니다.

### 5.8 답장

책상 주인이 받은 편지에 보내는 일방향 답장입니다. 한 편지에는 답장을 한 번만 보낼 수 있습니다.

`POST /api/desks/me/replies`

```json
{
  "scope": "SINGLE",
  "sourceMessageId": 10,
  "targetMessageIds": [10],
  "text": "고마워! 덕분에 힘이 났어"
}
```

- `scope=DAILY`는 프론트가 "오늘 받은 응원"의 대상 편지 ID를 모아 `targetMessageIds`로 보냅니다(1~100개).
- 대상은 모두 내 책상의 **열린**, 삭제되지 않은 편지여야 합니다(아니면 `400 INVALID_REPLY`). 이미 답장한 편지가 있으면 `409 REPLY_ALREADY_EXISTS`입니다.
- `text`는 1~500자입니다. 응답 `201 Created`:

```json
{
  "id": 1,
  "scope": "SINGLE",
  "sourceMessageId": 10,
  "targetMessageIds": [10],
  "targetSenderNames": ["민준"],
  "ownerName": "지수",
  "text": "고마워! 덕분에 힘이 났어",
  "createdAt": "2026-10-12T04:20:00Z"
}
```

`GET /api/desks/me/replies`: 내가 보낸 답장 전체(최신순, 같은 응답 형식의 배열). 편지에 이미 답장했는지 표시할 때 사용합니다.

### 5.9 숨긴 작성자 관리

숨긴 작성자의 편지는 **내 받은 편지 목록과 공개 편지 목록에서 모두 빠집니다.** 작성자는 숨겨졌는지 알 수 없고 편지는 계속 접수됩니다. 작성자는 회원이면 계정, 비회원이면 쿠키 단위로 구분합니다(같은 이름이라도 다른 사람이면 따로 숨깁니다).

| API | 설명 |
| --- | --- |
| `GET /api/desks/me/supporters` | 내 책상에 편지를 남긴 작성자 목록 |
| `POST /api/desks/me/blocks` | `{ "deliveryId": 21 }`의 작성자를 숨김. `201` (이미 숨겼다면 기존 항목 반환) |
| `DELETE /api/desks/me/blocks/{blockId}` | 숨김 해제 |

```json
[
  {"deliveryId": 21, "displayName": "민준", "messageCount": 2, "blocked": false, "blockId": null}
]
```

`deliveryId`는 해당 작성자의 대표(가장 최근) 편지입니다. 삭제된 편지만 남긴 작성자는 목록에 나오지 않습니다.

### 5.10 친구를 위한 책상(만들기·넘겨받기·삭제)

친구가 대신 만든 책상을 받는 사람이 넘겨받는 흐름입니다. 모두 로그인이 필요합니다.

```text
POST   /api/desks/for-friend      친구를 위한 책상 만들기 (본문은 5.1과 같음, displayName은 받는 사람 이름)
GET    /api/desks/for-friend      내가 만든, 아직 전달하지 않은 책상 목록
DELETE /api/desks/{deskId}        책상 삭제(현재 주인만)
GET    /api/claims/{claimToken}   받은 링크 미리보기
POST   /api/claims/{claimToken}   넘겨받기 { "confirmedRecipient": true }
```

- 만들면 링크가 둘 생깁니다. 응원을 남길 친구들에게는 `supporterToken`(기존 공유 링크), 책상의 주인에게는 `claimToken`을 전달합니다. 응답은 `FriendDeskResponse`(`id`, `displayName`, `supporterToken`, `claimToken`, 열람 방식, `messageCount`, `friendCount`, `createdAt`)입니다.
- **만든 사람은 응원 내용을 읽을 수 없습니다.** 받은 응원 API(`/api/desks/me/**`)는 주인 전용이라 열리지 않고, 만든 사람은 개수(`messageCount`, `friendCount`)만 봅니다.
- 한 사람이 아직 전달하지 않은 책상을 10개까지만 만들 수 있습니다(`409 TOO_MANY_FRIEND_DESKS`).
- **미리보기**는 `displayName`, `creatorName`, 열람 방식, `messageCount`, `friendCount`만 보여 줍니다. 내용은 보여 주지 않습니다.
- **넘겨받기**는 `confirmedRecipient`가 `true`일 때만 처리합니다. 프론트는 "OO님 본인이 맞나요?"를 물은 뒤 보내야 하며, 없거나 false면 `400 CLAIM_CONFIRMATION_REQUIRED`입니다.
  - 응답은 `{ desk: 주인용 책상(5.2와 같음), messageCount, friendCount }`입니다. 넘겨받기 전에 쌓인 응원은 "먼저 와 있던 응원"으로 남습니다.
  - 링크는 한 번만 쓸 수 있고(`claimToken`을 비웁니다), 쓴 뒤·삭제된 뒤·없는 링크는 `404 CLAIM_NOT_AVAILABLE`입니다. 같은 링크로 동시에 시도해도 한 명만 성공합니다.
  - 이미 자기 책상이 있는 사람은 넘겨받을 수 없습니다(`409 DESK_ALREADY_EXISTS`). 책상은 계정당 하나이고 합치기는 지원하지 않습니다.
  - 만든 사람 본인이 넘겨받는 것은 허용합니다.
  - 넘겨받으면 만든 사람의 권한은 끝나고 일반 서포터가 됩니다.
- **삭제**는 현재 주인만 할 수 있습니다. 넘겨받기 전에는 만든 사람, 넘겨받은 뒤에는 받은 사람입니다(본인이 만든 책상도 같습니다). 주인이 아니거나 없는 책상은 `404 DESK_NOT_FOUND`입니다. 삭제하면 서포터 링크·넘겨받기 링크가 모두 열리지 않고, 삭제한 주인은 새 책상을 만들 수 있습니다. 응원 기록은 DB에 남습니다.

### 5.10 답장 알림(웹 푸시)

책상 주인이 답장을 보내면, 그 답장에 포함된 편지를 쓴 사람의 브라우저로 알림을 보냅니다. 알림 문구는 고정(`응원에 답장이 도착했어요`)이고 **답장 내용은 담지 않습니다**(잠금 화면에 보이므로). 눌렀을 때 `/desk/{supporterToken}/replies`로 이동합니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/public/push/key` | `{ "enabled": true, "publicKey": "…" }` — 서버에 VAPID 키가 설정돼 있을 때만 `enabled`. 브라우저가 구독할 때 이 공개키를 씁니다 |
| PUT | `/api/public/push/subscription` | 구독 저장 `{ "endpoint": "https://…", "keys": { "p256dh": "…", "auth": "…" } }` → `204`. 로그인 회원 또는 응원을 남긴 적 있는(쿠키가 있는) 비회원만 가능(`401 IDENTITY_REQUIRED`) |
| DELETE | `/api/public/push/subscription` | `{ "endpoint": "…" }` — 본인 구독만 지움. 없거나 남의 것이어도 `204` |

- `endpoint`는 **알려진 푸시 서비스 주소만** 받습니다(`fcm.googleapis.com`, `*.push.services.mozilla.com`, `*.push.apple.com`, `*.notify.windows.com`, https, 기본 포트). 서버가 이 주소를 직접 호출하므로, 다른 주소는 `400 INVALID_PUSH_SUBSCRIPTION`입니다(서버 내부 주소로 요청을 보내게 하는 공격 방지). `p256dh`는 65바이트 공개키, `auth`는 16바이트 비밀(둘 다 base64url)이어야 합니다.
- 같은 브라우저가 다시 구독하면 한 줄만 유지하고(구독한 사람은 지금 사람으로 바뀜), 계정(또는 비회원 쿠키) 하나에 최대 5개 브라우저까지입니다(넘으면 `429 TOO_MANY_EVENTS`).
- VAPID 키가 없으면 `GET /key`는 `enabled: false`이고 구독은 `503 PUSH_NOT_AVAILABLE`입니다.
- 알림은 답장이 커밋된 뒤 서버 안의 별도 스레드가 보내며, 실패해도 답장에는 영향이 없습니다. 푸시 서비스가 `404`·`410`을 돌려주면(브라우저가 구독을 지움) 구독을 지우고, 연속 5번 실패해도 지웁니다. 180일 동안 전달이 한 번도 안 된 구독은 정리 배치가 지웁니다. 회원 탈퇴 처리 때 그 회원의 구독도 지웁니다.
- 암호화는 RFC 8291(aes128gcm), 서버 신원 증명은 VAPID(RFC 8292)이며 JDK 암호 기능만 씁니다(표준 문서의 예시 값으로 검증하는 테스트 포함).

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

`placement`: `HOME`, `DESK`, `MESSAGE_COMPLETE` (필수, 잘못된 값은 `400 INVALID_REQUEST`)

현재 시간이 노출 기간 안에 있고 활성화된 광고만 배열로 반환합니다. 광고가 없으면 `[]`입니다.

응답 `200 OK`:

```json
[
  {
    "id": 1,
    "title": "응원 캠페인",
    "creativeUrl": "https://cdn.example.com/ad.webp",
    "destinationUrl": "https://example.com/campaign",
    "placement": "DESK",
    "startsAt": "2026-10-04T00:00:00Z",
    "endsAt": "2026-11-30T23:59:59Z",
    "active": true
  }
]
```

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

응답 `201 Created`: `{ "eventId": 15 }` (중복 재시도도 성공 응답)

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

응답 `201 Created`: `{ "eventId": 42 }` (서버 저장 ID)

`eventId`는 이벤트마다 생성한 고유 값이며 네트워크 재시도에는 같은 값을 사용합니다. 클라이언트가 보낼 수 있는 `eventType`은 `PAGE_VIEW`, `MESSAGE_COMPOSE_STARTED`입니다.

`INVITE_LINK_OPEN`, `MESSAGE_CREATED`는 서버에서만 자동 기록합니다. 광고 이벤트와 트래픽 이벤트는 각각 회원 또는 비회원 식별자별 분당 120건으로 제한하며, 운영 환경에서는 게이트웨이/IP 단위 제한도 함께 설정해야 합니다. **쿠키가 없는(또는 모르는 쿠키인) 접속자가 방문·광고 이벤트를 보내면 새 손님 기록을 만드는데, 이 새 손님 생성은 접속자(IP 해시)당 분당 30건, 전체 분당 600건으로 제한하며 넘으면 `429 TOO_MANY_EVENTS`입니다(쿠키 버리고 반복해서 손님 행을 무한히 만드는 남용 방지).** 이미 쿠키가 있는 손님과 로그인 회원, 응원 작성·결제는 이 제한 대상이 아닙니다. 접속자 구분에는 `X-Forwarded-For`의 가장 왼쪽 값을 쓰며 저장하지 않습니다. IP와 User-Agent 원문은 저장하지 않습니다.

### 7.4 클라이언트 오류 보고

`POST /api/public/client-errors`

인증: 불필요. 사용자 브라우저에서 난 오류를 운영자에게 알립니다. 서버는 내용을 **저장하지 않고** 정리(이메일·토큰·긴 비밀값 가림, 쿼리 제거)해서 운영자 알림(디스코드)으로만 보냅니다.

```json
{
  "kind": "api_error",
  "message": "Internal server error",
  "route": "/prototype/my/desk",
  "apiPath": "/api/public/desks/abc",
  "status": 500,
  "code": "INTERNAL_SERVER_ERROR",
  "stack": "…",
  "buildId": "1b72ebd",
  "reportId": "r-ab12cd"
}
```

- `kind`: `js_error` `unhandled_rejection` `render_error` `api_error` `network_error` (그 외 값은 `400`). `message`는 필수(최대 400자).
- 응답 `202 Accepted`(본문 없음). 같은 접속자가 분당 10건, 전체가 분당 120건을 넘으면 `429 TOO_MANY_EVENTS`입니다.
- 알림에는 화면 경로·요청 경로·상태·브라우저 종류(카카오톡 인앱 여부 포함)·빌드 ID·접수 코드만 실립니다. IP와 User-Agent 원문은 저장·전송하지 않습니다.
- 같은 오류는 5분에 한 번만 알리고, 그 사이 횟수는 다음 알림에 합쳐서 표시합니다. 전체 알림은 분당 20건으로 제한합니다.
- 서버가 직접 겪는 오류(처리되지 않은 예외, 카카오·구글·토스 연동 같은 5xx 비즈니스 오류)도 같은 채널로 알립니다.

### 7.5 미니게임

미니게임 점수와 통계는 서버가 정합니다. 모두 로그인 없이 호출할 수 있고, `Authorization: Bearer`를 같이 보내면 회원으로 기록됩니다. **랭킹에는 로그인 회원만 오르며**(비회원은 점수와 정답만 받음), 랭킹은 미니게임 화면에서만 보여 줍니다.

**오늘의 찍신은 나야!** (정답은 서버가 정하고 제출 전에는 내려가지 않습니다. 한 판은 한 번만 제출할 수 있고 24시간 안에 내야 합니다.)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/public/minigames/guess-god/rounds` | 새 판 만들기. 접속자당 시간당 60판 |
| POST | `/api/public/minigames/guess-god/rounds/{roundId}/submit` | 답 제출·채점 |
| GET | `/api/public/minigames/guess-god/leaderboard?period=daily\|overall` | 랭킹 상위 20명 + 내 순위 + 점수 분포 |

```json
// POST .../rounds → 201
{ "roundId": "…uuid…", "questionCount": 10, "choiceCount": 5, "expiresAt": "…" }

// POST .../submit  요청: 문항별 고른 번호(1~5), 고르지 않은 문항은 null. 길이는 questionCount와 같아야 합니다.
{ "answers": [3, 1, null, 5, 2, 2, 4, 1, 3, 5] }
// → 200
{ "score": 4, "questionCount": 10, "answerKey": [3,1,4,5,2,1,1,1,2,5],
  "ranked": true,                                   // 로그인 회원이 낸 경우만 true
  "rank": { "daily": 12, "overall": 40, "bestScoreToday": 4, "bestScoreOverall": 6 },   // 비회원이면 null
  "betterThanPercent": 63 }                         // 전체 제출 중 나보다 낮은 점수의 비율

// GET .../leaderboard
{ "period": "daily",
  "entries": [{ "rank": 1, "nickname": "찍신왕", "score": 9, "me": false }],   // 동점은 같은 순위, 회원별 최고 점수
  "me": { "rank": 12, "score": 4 },               // 로그인했고 기간 안에 제출했을 때만, 아니면 null
  "distribution": [0, 3, 21, …],                  // 길이 11, distribution[i] = i점을 받은 판 수(비회원 포함)
  "totalRounds": 480 }
```

`daily`는 한국 시간 0시부터입니다. 닉네임을 정하지 않은 회원은 이름 첫 글자만 `홍**`처럼 보입니다. 탈퇴한 회원은 랭킹에서 빠집니다.

**선생님 몰래 폰 하기**

게임은 브라우저에서 돌아가므로 결과를 완전히 믿을 수는 없습니다. 서버는 **간단한 검사**만 합니다: 판을 서버가 발급해 한 번만 제출하게 하고, 점수가 폰을 든 시간으로 낼 수 있는 최대값(규칙상 끊김 없이 들었을 때)을 넘지 않는지, 판을 만든 뒤 게임 시간이 실제로 지났는지 봅니다. 랭킹은 로그인 회원만 오릅니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/public/minigames/sneaky-phone/rounds` | 게임을 **시작할 때** 판 만들기. 접속자당 시간당 100판 |
| POST | `/api/public/minigames/sneaky-phone/rounds/{roundId}/submit` | 게임이 끝났을 때 결과 제출 |
| GET | `/api/public/minigames/sneaky-phone/leaderboard?period=daily\|overall` | 상위 20명 + 내 순위 |

```json
// POST .../rounds → 201
{ "roundId": "…uuid…", "gameMs": 30000, "expiresAt": "…" }

// POST .../submit  요청 (프런트의 SneakyPhoneResult에서 status·baseScore·endedAt·totalPhoneMs만 보내면 됩니다. 시간은 밀리초)
{ "status": "survived", "baseScore": 6000, "endedAt": 30000, "totalPhoneMs": 25000 }
// → 200  (보너스 500점과 최종 점수는 서버가 계산)
{ "finalScore": 6500, "baseScore": 6000, "bonus": 500, "ranked": true,
  "rank": { "daily": 3, "overall": 12, "bestScoreToday": 6500, "bestScoreOverall": 6500 } }   // 비회원이면 ranked=false, rank=null

// GET .../leaderboard → 찍신 랭킹과 같은 모양에서 distribution·totalRounds만 없음
{ "period": "daily", "entries": [{ "rank": 1, "nickname": "찍신왕", "score": 8100, "me": false }], "me": { "rank": 3, "score": 6500 } }
```

`status`는 `caught`(들킴) 또는 `survived`(30초 생존)입니다. 검사에 걸리면 `400 INVALID_MINIGAME_RESULT`이고 판은 그대로 남아 올바른 값으로 다시 낼 수 있습니다. 판은 **게임을 시작하는 순간** 만들어야 합니다(끝난 뒤에 만들면 게임 시간이 지나지 않은 것으로 보여 거절됩니다). 최고 점수가 아닌 판도 모두 제출해도 됩니다. 랭킹에는 회원별 최고 점수만 올라갑니다.

**나의 수험생 유형은?**

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/public/minigames/study-type/results` | 결과 유형 기록 `{"code":"PSA"}` (8개 코드 `PSA PSN PFA PFN LSA LSN LFA LFN`). 접속자당 10분에 20번 |
| GET | `/api/public/minigames/study-type/stats` | 유형별 사람 수와 비율 |

```json
// 두 API 모두 같은 모양 (POST는 201)
{ "total": 1520, "myCode": "PSA",                  // myCode는 로그인 회원이 한 번이라도 기록했을 때만
  "distribution": [{ "code": "PSA", "count": 210, "percent": 13.8 }, …8개] }
```

결과 화면에 들어올 때마다가 아니라 **테스트를 끝낸 순간 한 번만** 기록해 주세요. 회원은 다시 해도 한 줄만 남고 유형이 바뀌며, 비회원은 기록할 때마다 한 줄씩 늘어납니다(참고용 통계입니다).

**수능 끝 D+1 코스 짜기**

게임이 끝났을 때 한 번 보내는 응답입니다. **통계용으로만** 쓰며 이름·연락처·계정·IP를 저장하지 않습니다(로그인 토큰 없이 보내고, 보내도 연결하지 않습니다).

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/public/minigames/d1-course/responses` | 응답 저장. 새 응답은 `201`, 같은 응답을 다시 보내면 `200` |

```json
{ "responseId": "…uuid(판마다 난수)…", "startRegion": "gyeonggi", "startDistrict": "고양시",
  "area": "hongdae", "activities": ["dye", "lens", "food"], "companion": "small", "budget": "30kTo100k", "shared": false }
// → { "received": true }
```

- 선택지 id는 프런트 `d1CourseConfig.ts`·`d1CourseRegions.ts`와 같습니다. `startRegion`은 17개 시·도 id, `startDistrict`는 한글 이름(10자 이내, 세종은 `null`), `area`·`companion`·`budget`은 정해진 id, `activities`는 서로 다른 활동 id **정확히 3개**입니다. 모르는 id나 형식이 틀리면 `400 INVALID_D1_COURSE_RESPONSE`(또는 형식 검증 `400`)입니다. **프런트에 선택지를 추가하면 백엔드(`D1CourseService`)도 같이 늘려야 합니다.**
- `responseId`가 같으면 같은 응답으로 보고 **`shared`만 `true`로 올립니다**(공유 버튼을 누른 뒤 한 번 더 보냄). 답 내용은 처음 받은 그대로이고 `shared`는 다시 내려가지 않습니다.
- 같은 IP는 시간당 60건을 넘으면 `429 TOO_MANY_EVENTS`입니다.

**랭킹 닉네임** (로그인 필수)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/minigames/me/nickname` | `{ "nickname": null \| "찍신왕", "defaultNickname": "홍**" }` |
| PUT | `/api/minigames/me/nickname` | `{ "nickname": "찍신왕" }` — 앞뒤 공백을 지운 2~12자, 한글·영문·숫자·밑줄·공백만. 욕설·성적·혐오·자해·운영진 사칭 단어가 들어 있으면 `400 NICKNAME_NOT_ALLOWED`(공백·숫자를 끼워 넣어도 걸러짐) |

## 8. 운영자 API

Bearer 인증이 필요하며 `users.admin`이 켜진 계정만 접근할 수 있습니다. 권한은 어드민 화면의 운영자 관리(8.4)로 부여·회수하고, 환경변수 `ADMIN_KAKAO_IDS`·`ADMIN_GOOGLE_IDS`에 있는 계정은 로그인할 때 항상 운영자가 됩니다(회수해도 다음 로그인에 복구되므로, 환경변수는 비상용으로만 둡니다).

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

응답:

| API | 상태 | 본문 |
| --- | --- | --- |
| `GET /ads` | 200 | 광고 배열 (페이지네이션 없음, 7.1의 광고 객체와 동일) |
| `POST /ads` | 201 | 광고 객체 |
| `PATCH /ads/{id}` | 200 | 광고 객체 (요청 본문은 생성과 동일, 전체 값 필요) |
| `DELETE /ads/{id}` | 200 | `{ "message": "광고가 비활성화되었습니다." }` |
| `GET /ads/{id}/metrics` | 200 | 아래 예시 |
| `GET /analytics/summary` | 200 | 아래 예시 |

```json
// metrics
{
  "advertisementId": 1,
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-08T00:00:00Z",
  "events": {"IMPRESSION": 120, "VIEW_COMPLETED": 40, "CLICK": 9}
}

// summary
{
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-08T00:00:00Z",
  "trafficEvents": {"PAGE_VIEW": 300, "INVITE_LINK_OPEN": 80, "MESSAGE_CREATED": 25},
  "advertisementEvents": {"IMPRESSION": 120, "CLICK": 9}
}
```

건수가 0인 이벤트 종류는 맵에 포함되지 않을 수 있습니다. 광고 요청은 `endsAt`이 `startsAt`보다 늦어야 하고 URL은 `http(s)`여야 합니다(`INVALID_ADVERTISEMENT`).

`DELETE`는 이력을 유지한 채 광고를 비활성화합니다. 광고별 지표는 해당 광고의 노출·시청·클릭 건수를 반환합니다. 전체 분석 요약은 트래픽과 광고 이벤트 종류별 건수를 반환합니다. 조회 범위는 최대 366일이며 기간을 생략하면 최근 7일입니다.

### 8.0 상담 신청 관리

```text
GET   /api/admin/promotion-inquiries?status=NEW&page=0&size=20   (status 생략 시 전체, 최신순)
PATCH /api/admin/promotion-inquiries/{id}   { "status": "CONTACTED" }   NEW | CONTACTED | CLOSED
```

- 응답은 `SliceResponse<{ id, company, contactName, email, phone, category, interest, message, status, createdAt, handledAt }>`입니다. 없는 id는 `404 INQUIRY_NOT_FOUND`입니다.
- 상태 변경은 `admin_audit_logs`(`PROMOTION_INQUIRY_STATUS`)에 남습니다.

### 8.1 신고 관리

```text
GET   /api/admin/reports?status=OPEN&page=0&size=20
PATCH /api/admin/reports/{reportId}
```

`status` 기본값은 `OPEN`. `PATCH` 본문 `{ "action": "DISMISS" }`는 신고를 기각(`DISMISSED`), `{ "action": "REMOVE_MESSAGE" }`는 편지를 삭제 처리하고 신고를 해결(`RESOLVED`)로 표시합니다.

```json
{
  "id": 1, "messageId": 1, "reason": "욕설", "status": "OPEN",
  "messageAuthorDisplayName": "민준", "messageVisibility": "PUBLIC", "messageStatus": "READ",
  "createdAt": "2026-10-12T04:22:47Z", "resolvedAt": null
}
```

### 8.2 회원 관리

모두 운영자(Bearer) 전용입니다.

```text
GET  /api/admin/users?query=&state=ACTIVE|DELETED|ALL&page=0&size=20
GET  /api/admin/users/{userId}
POST /api/admin/users/{userId}/withdraw      { "confirmForfeitBalance": false }
```

- 목록: `query`는 이름·이메일·로그인 식별자의 일부 또는 회원 번호입니다. 기본은 탈퇴하지 않은 회원(`ACTIVE`), 가입일 최신순입니다. 행: `id, provider, displayName, email, profileImageUrl, admin, createdAt, lastLoginAt, deletedAt`.
- 상세: 위 정보에 책상(`deskId`, `deskClosed`), 받은 응원 수, 보낸 응원 수, 찰떡 잔액, 최근 관리자 작업 기록(`history`)이 붙습니다.
- **탈퇴 처리**는 행을 지우지 않고 **개인정보를 지우는 익명화**입니다(응원·결제 장부의 연결을 보존하기 위함).
  - 이름은 `탈퇴한 사용자`, 이메일·프로필 사진은 삭제, 로그인 식별자는 `deleted:{id}`로 바뀝니다. 같은 카카오·구글 계정으로 다시 로그인하면 **새 회원**이 만들어집니다.
  - 로그인 유지(리프레시) 토큰은 모두 삭제됩니다. 이미 발급된 액세스 토큰은 만료(30분)까지 유효합니다.
  - 그 회원이 보낸 응원은 **남고** 작성자 표시만 `탈퇴한 사용자`로 바뀝니다. 그 회원의 책상은 닫히고(새 응원 불가) 이름이 지워지며, 받은 응원은 남습니다.
  - 찰떡이 남아 있으면 `409 BALANCE_REMAINING`이고, `confirmForfeitBalance: true`로 소멸에 동의해야 진행합니다. 결제·충전 장부는 법정 보존을 위해 남습니다.
  - 운영자 계정과 본인은 탈퇴 처리할 수 없습니다(`409 ADMIN_ACCOUNT_PROTECTED`). 이미 처리한 회원을 다시 호출하면 그대로 성공합니다.
  - 처리 내용은 `admin_audit_logs`에 기록됩니다.

### 8.3 통계

```text
GET /api/admin/stats/daily?from=2026-10-01&to=2026-10-14     날짜별(한국 시간)
GET /api/admin/stats/regions?from=…&to=…                     접속 지역(시·도)
```

- 날짜는 `yyyy-MM-dd`이고 **한국 시간(Asia/Seoul)** 기준입니다. 생략하면 일별은 최근 14일, 지역은 최근 30일이며 최대 92일까지입니다(초과·역순은 `400`).
- 일별: `date, visitors(회원+비회원 고유 접속자), memberVisitors, guestVisitors, pageViews, signups, desksCreated, messagesCreated, chargeAmount(승인된 충전 금액)`. 활동이 없는 날도 0으로 포함하고, 응답 앞부분에 전체 `totalUsers`(탈퇴 제외)·`totalDesks`·`totalMessages`가 옵니다.
- 지역: `country, region, regionName(서울·부산 등), visitors, pageViews`를 조회수 순으로, 위치를 알 수 없는 조회는 `unknownPageViews`로, 한국이 아닌 접속은 표에 넣지 않고 `overseasPageViews`로 건수만 따로 셉니다(일별 접속자 수에는 그대로 포함).
- **접속자·지역은 프론트가 `POST /api/public/analytics/events`로 `PAGE_VIEW`를 보낼 때만 집계됩니다.** 위치는 프론트가 호스팅의 `/api/geo`(Vercel 함수)에서 받아 방문 기록 본문의 `geoCountry`(2자)·`geoRegion`(시·도 코드)에 실어 보낸 값을 씁니다. 요청 헤더에 위치가 있으면 헤더가 우선이며, 그 헤더는 프론트 호스팅 미들웨어가 `/api/*` 요청에 붙이는 `X-Chak-Geo-Country`, `X-Chak-Geo-Region` 값(국가·시도 코드)만 저장하며(없으면 `X-Vercel-IP-Country`, `X-Vercel-IP-Country-Region`을 대신 사용. Vercel은 외부 주소로 넘기는 rewrite에는 자기 헤더를 붙이지 않아 미들웨어가 필요함) **IP는 저장하지 않습니다.**

### 8.4 운영자 관리

```text
GET    /api/admin/admins                       운영자 목록 + 대기 중인 초대
POST   /api/admin/admins                       { "email": "name@example.com" } 권한 부여
DELETE /api/admin/admins/users/{userId}        운영자 권한 회수
DELETE /api/admin/admins/invites/{inviteId}    대기 중인 초대 취소
```

- 응답은 모두 `{ admins: [회원 요약], invites: [{ id, email, createdAt, consumedAt, bootstrap }] }`입니다.
- 부여는 **구글이 확인한 이메일**에만 적용됩니다. 같은 이메일의 구글 계정이 이미 있으면 바로 운영자가 되고, 없으면 초대로 저장했다가 그 이메일로 처음 로그인할 때 적용합니다. 이메일은 소문자로 정규화하며 같은 요청을 반복해도 결과는 같습니다. 카카오는 이메일을 받지 않아 적용되지 않습니다.
- 본인 권한은 회수할 수 없습니다(`409 ADMIN_SELF_REVOKE`). 운영자가 아닌 회원을 회수하면 `409 NOT_AN_ADMIN`, 형식이 틀린 이메일은 `400`입니다.
- 최초 운영자는 마이그레이션(V10)이 `mj0324@yonsei.ac.kr` 초대를 심어 둡니다.
- 모든 작업은 `admin_audit_logs`에 남습니다(`GRANT_ADMIN`, `INVITE_ADMIN`, `REVOKE_ADMIN`, `CANCEL_ADMIN_INVITE`).

### 8.5 미니게임 참여 통계

```text
GET /api/admin/minigames/stats
```

```json
{
  "games": [{
    "game": "GUESS_GOD",                 // 또는 SNEAKY_PHONE
    "startedToday": 120, "submittedToday": 96,       // 오늘 판을 받은 수 / 끝까지 제출한 수
    "submittedLast7Days": 540, "submittedLast14Days": 980,
    "averageScoreLast14Days": 3.1,        // 기록이 없으면 null
    "rankedMembers": 41, "bestMemberScore": 9,       // 지금까지 랭킹에 오른 회원 수 / 회원 최고 점수
    "days": [{ "date": "2026-10-12", "members": 14, "guests": 52 }]   // 최근 14일(한국 시간), 제출 기준
  }],
  "studyType": {
    "total": 1520, "members": 210, "today": 33, "last7Days": 240,
    "distribution": [{ "code": "PSA", "count": 210, "percent": 13.8 }]
  },
  "d1Course": {                         // D+1 코스 짜기
    "total": 480, "today": 20, "last7Days": 310, "shared": 96,
    "areas": [{ "key": "hongdae", "count": 130 }],         // 많은 순. 활동은 한 응답이 3개를 고르므로 합이 응답 수의 3배
    "activities": [{ "key": "food", "count": 300 }], "companions": [], "budgets": [], "regions": []
  }
}
```

- 운영자만 호출할 수 있습니다. 규모가 작아 행을 읽어 자바에서 집계합니다.
- 비회원이 제출한 판은 정리 배치가 30일 뒤, 제출하지 않은 판은 2일 뒤 지우므로 일별 표는 최근 14일만 줍니다. `startedToday − submittedToday`가 오늘 중간에 그만둔 판입니다.
- `rankedMembers`·`bestMemberScore`는 탈퇴한 회원의 기록도 포함합니다(랭킹 화면에서는 빠집니다).

## 9. 보낸 응원·반응·신고 (작성자·방문자)

모두 `/api/public/**`이며 로그인은 선택입니다. 회원은 Bearer, 비회원은 `chak_guest_id` 쿠키(`credentials: "include"`)로 본인을 식별합니다. 신원을 확인할 수 없으면 `401 IDENTITY_REQUIRED`(조회 API는 빈 결과)입니다.

### 9.1 내가 보낸 응원

`GET /api/public/messages/mine?page=0&size=20`

내가 쓴 편지(삭제 제외)를 최신순으로 반환합니다. `page`는 0부터, `size`는 1~100입니다.

```json
{
  "content": [
    {
      "messageId": 10,
      "deskName": "지수",
      "supporterToken": "550e8400-e29b-41d4-a716-446655440000",
      "kind": "CARD",
      "visibility": "PUBLIC",
      "status": "SENT",
      "deletable": true,
      "schemaVersion": 1,
      "card": {"pages": []},
      "object": {},
      "unlockAt": "2026-10-12T13:00:00Z",
      "readAt": null,
      "createdAt": "2026-10-12T03:26:13Z"
    }
  ],
  "page": 0, "size": 20, "hasNext": false
}
```

`deletable`은 공개 편지이면서 아직 열어보지 않은 경우(`status=SENT`)입니다.

### 9.2 내 편지 삭제

`DELETE /api/public/messages/{messageId}` → `200 {"message": "편지가 삭제되었습니다."}`

내가 쓴 **공개 편지이면서 책상 주인이 아직 읽지 않은** 편지만 삭제할 수 있습니다. 남의 편지는 `404 MESSAGE_NOT_FOUND`, 이미 읽혔거나 비공개이면 `409 MESSAGE_NOT_DELETABLE`입니다. 삭제는 soft delete입니다.

### 9.3 반응

한 사람은 한 편지에 하나의 반응만 남길 수 있고, 다른 반응을 보내면 바뀝니다.

| API | 설명 |
| --- | --- |
| `PUT /api/public/messages/{messageId}/reaction` | `{ "type": "HEART" }` → `{ "messageId": 10, "myReaction": "HEART" }` |
| `DELETE /api/public/messages/{messageId}/reaction` | 반응 취소 |
| `GET /api/public/messages/reactions?ids=10,11` | 내가 남긴 반응 `{ "10": "HEART" }` (반응 없는 편지는 생략, 최대 200개) |

반응할 수 있는 편지: ① 책상 주인은 열린 모든 편지 ② 그 밖의 사람은 열렸고 `PUBLIC`이며 공개 목록이 켜져 있고 주인이 숨기지 않은 작성자의 편지. 그 외는 존재하지 않는 것처럼 `404 MESSAGE_NOT_FOUND`입니다.

### 9.4 신고

`POST /api/public/messages/{messageId}/report` `{ "reason": "욕설" }`(본문·`reason`은 선택, 최대 100자) → `201`

반응과 같은 접근 규칙이 적용되며, 같은 사람이 같은 편지를 여러 번 신고해도 한 번만 접수됩니다. 처리는 [8.1 신고 관리](#81-신고-관리)에서 합니다.

### 9.5 내 편지에 달린 답장

`GET /api/public/replies/mine`

내가 보낸 편지에 책상 주인이 답장한 목록입니다(최신순, [5.8](#58-답장)과 같은 형식). `targetMessageIds`·`targetSenderNames`에는 **내 편지만** 들어갑니다.

## 10. 결제와 광고 해제

### 10.1 결제

서버가 상품 단가를 가지고 있으며 **주문 금액은 항상 서버가 계산**합니다. 프론트의 표시 가격은 아래와 같아야 합니다.

| ProductCode | 단가(원) | 사용처 |
| --- | --- | --- |
| `CHARM_ACRYLIC` | 50 | 책상 부적 · 아크릴 3D |
| `GEM` | 5 | 보석 스티커(개당) |
| `UNIVERSITY_CHARM` | 150 | 교실 대학 굿즈 부적 (교실 기능과 함께 제공 예정) |
| `PENNANT` | 150 | 교실 대학 깃발 (교실 기능과 함께 제공 예정) |
| `LOCKER_BULB` | 200 | 교실 사물함 전구 (교실 기능과 함께 제공 예정) |
| `LOCKER_PAINT` | 200 | 교실 사물함 페인트 (교실 기능과 함께 제공 예정) |

흐름: ① 주문 생성 → ② 승인(confirm) → ③ 결제한 `orderId`를 편지 전송에 첨부.

```text
POST /api/public/payments/orders               주문 생성
POST /api/public/payments/orders/{id}/confirm  승인 (본인 주문만)
GET  /api/public/payments/orders/{id}          주문 조회
```

```json
{ "items": [ {"code": "CHARM_ACRYLIC"}, {"code": "GEM", "quantity": 2} ] }
```

응답: `{ "orderId": 1, "status": "PAID", "totalAmount": 60, "consumed": false, "items": [...], "createdAt": "...", "paidAt": "..." }`. 항목은 최대 20개, 수량은 1~100입니다. 다른 사람의 주문은 `404 ORDER_NOT_FOUND`입니다.

**편지 전송 시 결제 검증**: 편지 작성 요청(4.3)의 `object.metadata`에 `"material": "acrylic"` 또는 `gems` 배열이 있으면 서버가 필요한 상품(`CHARM_ACRYLIC` 1개, `GEM` 보석 개수)을 계산하고, 요청의 `paymentOrderId`가 **결제 완료된 본인 주문**이며 해당 항목을 모두 포함하는지 확인합니다. 주문은 한 번만 쓸 수 있습니다.

| 상태 | 코드 | 의미 |
| --- | --- | --- |
| 402 | `PAYMENT_REQUIRED` | 주문이 없거나 결제 전이거나 필요한 항목을 포함하지 않음 |
| 409 | `ORDER_ALREADY_USED` | 이미 사용한 주문 |
| 503 | `PAYMENT_NOT_CONFIGURED` | 승인 수단이 설정되지 않음 |

> **승인 수단**: 현재는 실제로 돈이 오가지 않는 **mock 승인**(`PAYMENT_PROVIDER=mock`, 로컬·개발 기본값)만 있습니다. `prod` 프로필의 기본값은 `none`이라 승인이 `503`으로 거부됩니다. 실제 결제는 PG(예: 토스페이먼츠) 선택과 사업자 설정 후 `PaymentProvider`를 구현해 연결해야 합니다. 한 주문으로 여러 편지를 결제하거나 환불하는 기능은 아직 없습니다.

### 10.2 광고 시청으로 해제

광고를 끝까지 본 사람이 편지지·클립·마스킹 테이프를 해제합니다. 해제한 목록은 본인(회원 또는 같은 쿠키의 비회원)에게만 적용됩니다.

```text
GET  /api/public/ads?placement=UNLOCK     해제용 광고 조회 (7.1)
POST /api/public/ads/{id}/events          IMPRESSION → VIEW_STARTED → VIEW_COMPLETED (7.2, 같은 sessionId)
POST /api/public/unlocks                  { "category": "STATIONERY", "assetId": "bg-lace", "adSessionId": "..." }
GET  /api/public/unlocks                  [ { "category": "STATIONERY", "assetId": "bg-lace" } ]
```

- `placement=UNLOCK` 광고가 **하나라도 노출 중이면** 요청의 `adSessionId`로 같은 사람이 `VIEW_COMPLETED` 이벤트를 보낸 기록이 있어야 하며, 없으면 `403 AD_NOT_COMPLETED`입니다.
- 노출 중인 해제용 광고가 **없으면** 사용자를 막지 않기 위해 광고 없이 해제됩니다.
- 이미 해제한 항목을 다시 요청해도 성공(멱등)입니다.

### 10.3 찰떡(충전식 포인트)

회원 전용(`Authorization: Bearer`)입니다. 1원 = 1찰떡이며 상품 단가는 10.1과 같습니다. 비로그인 요청은 `401 UNAUTHORIZED`입니다.

```text
GET  /api/chaltteok/balance          { "balance": 1200, "customerKey": "…" }
POST /api/chaltteok/charges          { "amount": 1000 }  →  201 { "orderId", "amount", "orderName" }
POST /api/chaltteok/charges/confirm  { "paymentKey", "orderId", "amount" }  →  { "orderId", "chargedAmount", "balance" }
POST /api/chaltteok/purchases        { "items": [ { "code": "GEM", "ref": null, "quantity": 4 } ] }
                                     →  { "spent": 20, "balance": 1180, "orderId": 31 }
GET  /api/chaltteok/history?size=30  [ { "type": "CHARGE|PURCHASE", "amount", "balanceAfter", "ref", "createdAt" } ]
```

**충전 흐름**: `charges`로 주문번호를 받고 → 토스 결제위젯(`customerKey`·`orderId`·`amount`)으로 결제 → 성공 리다이렉트의 `paymentKey`로 `charges/confirm`을 호출하면 서버가 토스 승인 API를 호출해 금액을 검증한 뒤 잔액에 반영합니다.

- 충전 금액은 300원 이상 50,000원 이하 정수입니다(`400 INVALID_CHARGE_AMOUNT`). `orderId`는 서버가 발급하며 토스 규격(6~64자)을 만족합니다. 결제가 끝나지 않은 주문은 사용자당 최근 1시간에 10개까지입니다(`429 TOO_MANY_PENDING_CHARGES`).
- `confirm`의 `amount`가 주문 금액과 다르면 `400 AMOUNT_MISMATCH`, 본인 주문이 아니거나 없으면 `404 ORDER_NOT_FOUND`, 발급 후 1시간 안에 한 번도 승인을 시도하지 않은 주문은 `409 ORDER_EXPIRED`입니다. `orderId`는 `[A-Za-z0-9_=-]{6,64}`가 아니면 `400 INVALID_REQUEST`입니다.
- **멱등**: 이미 승인한 주문을 다시 `confirm`하면 토스를 다시 호출하지 않고 처음 결과를 그대로 돌려줍니다(새로고침·재시도 안전).
- **동시 요청**: 같은 주문을 동시에 `confirm`하면 하나만 승인을 처리하고 나머지는 `409 CHARGE_IN_PROGRESS`입니다. 잠시 후 같은 요청을 다시 보내면 처음 결과가 옵니다.
- **재시도 규칙**: `502 PAYMENT_CONFIRM_FAILED`(토스 통신 오류·토스 일시 오류)와 `409 CHARGE_IN_PROGRESS`는 **같은 주문으로 다시 `confirm`**하면 됩니다. 토스가 이미 승인했는데 서버 반영이 실패했던 경우에도 재시도하면 결제를 조회해 검증한 뒤 반영합니다. `400 PAYMENT_DECLINED`(카드 거절 등)는 결제를 다시 해야 합니다.
- **구매**: 서버가 단가로 합계를 계산해 잔액에서 차감하고, 결제 완료된 주문을 만들어 `orderId`를 돌려줍니다. 편지 전송 시 이 값을 `paymentOrderId`로 보내면 한 번 소비됩니다(전송 실패 시 같은 `orderId`로 재시도 가능).
- **구매 멱등(권장)**: `Idempotency-Key: <8~64자 [A-Za-z0-9_-]>` 헤더를 붙이면 같은 키의 재요청(더블클릭·네트워크 재시도)은 다시 차감하지 않고 처음 결과를 돌려줍니다. 같은 키에 다른 항목이면 `409 IDEMPOTENCY_KEY_REUSED`입니다. 사용자가 결제 버튼을 누를 때마다 새 키(UUID)를 만들고, 그 시도를 재시도할 때만 같은 키를 쓰세요.
- 한 항목 수량은 1~100, 한 번에 20종까지입니다(`400 INVALID_PURCHASE`/`INVALID_REQUEST`).
- 요청이 겹쳐 DB 충돌이 나면 `409 CONCURRENT_REQUEST`이며 아무것도 반영되지 않았으니 다시 시도하면 됩니다.
- 잔액이 부족하면 `402 INSUFFICIENT_CHALTTEOK`이고 본문에 `required`(필요한 찰떡)와 `balance`(현재 잔액)가 **최상위 필드**로 함께 옵니다.

```json
{ "code": "INSUFFICIENT_CHALTTEOK", "message": "찰떡이 부족합니다.", "required": 400, "balance": 300 }
```

> **승인 수단**: `CHALTTEOK_PROVIDER` = `mock`(로컬 기본, 항상 승인·돈 안 오감) / `toss`(토스페이먼츠 `POST /v1/payments/confirm`, `TOSS_SECRET_KEY` 필요) / `none`(`prod` 기본, 충전 `503 PAYMENT_NOT_CONFIGURED`). 시크릿 키는 서버에만 두며 프론트에는 클라이언트 키(`VITE_TOSS_CLIENT_KEY`)만 둡니다. 환불·결제 취소·찰떡 회수는 아직 없습니다.

## 11. 전체 에러 코드

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
| 401 | `INVALID_GOOGLE_AUTH_CODE` | 구글 인가 코드 만료·재사용·redirect_uri 불일치 |
| 401 | `INVALID_REFRESH_TOKEN` | 리프레시 토큰 오류 또는 누락(본문·쿠키 모두 없음) |
| 401 | `EXPIRED_REFRESH_TOKEN` | 리프레시 토큰 만료 |
| 403 | `FORBIDDEN` | 접근 권한 없음 |
| 403 | `GUEST_IMAGE_NOT_ALLOWED` | 비회원이 사진이 포함된 카드를 보냄 |
| 403 | `PUBLIC_FEED_DISABLED` | 공개 편지 목록 비활성화 |
| 403 | `ADMIN_REQUIRED` | 운영자 권한 없음 |
| 404 | `USER_NOT_FOUND` | 사용자 없음 |
| 404 | `DESK_NOT_FOUND` | 책상 없음 |
| 404 | `MESSAGE_NOT_FOUND` | 소유한 편지가 없음 |
| 404 | `MEDIA_NOT_FOUND` | 소유한 미디어 자산이 없음 |
| 404 | `ADVERTISEMENT_NOT_FOUND` | 광고 없음 |
| 409 | `DESK_CLOSED` | 편지 접수 종료 |
| 409 | `DESK_ALREADY_EXISTS` | 계정에 이미 책상이 있음(친구 책상 넘겨받기 포함) |
| 400 | `CLAIM_CONFIRMATION_REQUIRED` | 넘겨받기에 본인 확인(`confirmedRecipient: true`)이 없음 |
| 404 | `CLAIM_NOT_AVAILABLE` | 이미 쓴·삭제된·없는 넘겨받기 링크 |
| 409 | `TOO_MANY_FRIEND_DESKS` | 아직 전달하지 않은 친구 책상이 10개 |
| 409 | `MESSAGE_NOT_READ` | 읽지 않은 편지를 바구니로 이동 시도 |
| 409 | `MESSAGE_NOT_DELETABLE` | 이미 읽혔거나 공개 편지가 아니어서 작성자가 삭제할 수 없음 |
| 401 | `IDENTITY_REQUIRED` | 작성자를 확인할 수 없음(로그인 또는 비회원 쿠키 필요) |
| 409 | `BALANCE_REMAINING` | 남은 찰떡이 있는 회원의 탈퇴 처리(소멸 동의 필요) |
| 409 | `ADMIN_ACCOUNT_PROTECTED` | 운영자 계정·본인 탈퇴 처리 시도 |
| 409 | `REPLY_ALREADY_EXISTS` | 이미 답장한 편지가 포함됨 |
| 400 | `INVALID_REPLY` | 답장 대상(내 책상의 열린 편지가 아님) 또는 내용 오류 |
| 404 | `BLOCK_NOT_FOUND` | 숨긴 작성자를 찾을 수 없음 |
| 404 | `REPORT_NOT_FOUND` | 신고를 찾을 수 없음 |
| 402 | `PAYMENT_REQUIRED` | 결제가 필요하거나 주문이 항목을 포함하지 않음 |
| 400 | `INVALID_ORDER` | 주문 항목 오류 |
| 404 | `ORDER_NOT_FOUND` | 본인의 주문이 아님 |
| 409 | `ORDER_ALREADY_USED` | 이미 사용한 결제 |
| 503 | `PAYMENT_NOT_CONFIGURED` | 결제 승인 수단 미설정 |
| 400 | `INVALID_CHARGE_AMOUNT` | 찰떡 충전 금액이 300~50,000원 정수가 아님 |
| 400 | `AMOUNT_MISMATCH` | 승인 요청 금액이 충전 주문과 다름 |
| 400 | `INVALID_PURCHASE` | 찰떡 구매 항목 오류 |
| 402 | `INSUFFICIENT_CHALTTEOK` | 찰떡 부족(본문에 `required`, `balance` 포함) |
| 400 | `PAYMENT_DECLINED` | 토스가 결제를 승인하지 않음 |
| 502 | `PAYMENT_CONFIRM_FAILED` | 토스 승인 서버 통신·일시 오류(같은 주문으로 재시도) |
| 409 | `ORDER_EXPIRED` | 승인을 시도하지 않고 1시간이 지난 충전 주문 |
| 409 | `CHARGE_IN_PROGRESS` | 같은 충전 주문을 다른 요청이 처리 중(잠시 후 재시도) |
| 429 | `TOO_MANY_PENDING_CHARGES` | 결제 안 끝난 충전 주문이 10개를 넘음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`를 다른 내용으로 사용 |
| 409 | `CONCURRENT_REQUEST` | 요청 겹침으로 DB 충돌(반영 없음, 재시도) |
| 403 | `AD_NOT_COMPLETED` | 광고 시청 완료 기록이 없음 |
| 409 | `MESSAGE_LOCKED` | 공개 시각 전 읽기 요청 |
| 409 | `MEDIA_UPLOAD_NOT_FOUND` | S3에서 업로드 결과를 찾지 못함 |
| 429 | `TOO_MANY_EVENTS` | 이벤트·신고·주문 요청 한도 초과 |
| 429 | `TOO_MANY_MESSAGES` | 같은 접속자가 응원을 너무 자주 보냄 |
| 429 | `DESK_BUSY` | 한 책상에 응원이 한꺼번에 몰려 잠시 받지 않음 |
| 502 | `KAKAO_API_ERROR` | 카카오 API 통신 또는 설정 오류 |
| 502 | `GOOGLE_API_ERROR` | 구글 API 통신 또는 설정 오류 |
| 502 | `STORAGE_API_ERROR` | Object Storage 통신 오류 |
| 503 | `STORAGE_NOT_CONFIGURED` | Object Storage 환경변수 미설정 |
| 404 | `NOT_FOUND` | 존재하지 않는 경로 |
| 404 | `MINIGAME_ROUND_NOT_FOUND` | 없거나 24시간이 지난 게임 판 |
| 409 | `MINIGAME_ROUND_ALREADY_SUBMITTED` | 이미 제출한 판 |
| 400 | `INVALID_MINIGAME_RESULT` | 폰 게임 결과가 규칙·시간 검사에 맞지 않음 |
| 400 | `INVALID_MINIGAME_ANSWERS` | 답 개수가 문항 수와 다르거나 1~5가 아님 |
| 400 | `INVALID_NICKNAME` | 닉네임 형식 오류 |
| 400 | `INVALID_PUSH_SUBSCRIPTION` | 알림 구독 주소·키가 올바르지 않음(알려진 푸시 서비스가 아님 등) |
| 503 | `PUSH_NOT_AVAILABLE` | 서버에 알림 키가 설정되지 않음 |
| 400 | `INVALID_DISPLAY_NAME` | 프로필 이름 형식 오류 |
| 400 | `INVALID_AVATAR` | 프로필 사진 형식·크기·내용 오류 |
| 404 | `AVATAR_NOT_FOUND` | 프로필 사진을 찾을 수 없음 |
| 400 | `INVALID_D1_COURSE_RESPONSE` | D+1 코스 응답에 모르는 선택지·잘못된 조합 |
| 400 | `NICKNAME_NOT_ALLOWED` | 금칙어가 들어 있는 닉네임 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `application/json`이 아닌 Content-Type |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

## 12. 아직 제공하지 않는 API

- **교실 단위 응원**(교실·사물함·칠판·교실 Wrapped, 교실 상품 결제): Post-MVP 범위로 구현을 보류했습니다.
- 책상 연결 코드(`다른 응원 공간 연결`), 개인 Wrapped, 학생 혜택, 이미 책상이 있는 사람에게 친구 책상을 합치기
- 바구니에서 편지 꺼내기, 책상 주인의 편지 삭제 화면(삭제 API는 있음)
- 이미지 삭제 및 미완료 업로드 정리 배치(비회원 이미지 첨부는 정책상 허용하지 않음)
- 실제 결제 승인(PG 연동), 결제 환불, 알림 발송
- 상세 분석 대시보드용 일별 집계 테이블
