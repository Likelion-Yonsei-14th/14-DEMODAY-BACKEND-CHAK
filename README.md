# CHAK Backend

수험생 개인 응원 책상 서비스의 production backend입니다.

## Current scope

- Spring Boot / JPA / MySQL 프로젝트 기반
- 개인 책상 도메인
- 비회원 guest identity와 1년 HttpOnly cookie
- Supporter token 기반 개인 책상 조회
- 비회원 편지 작성
- 편지별 public/private 저장
- `nickname_override` 기반 작성자 표시 계약
- 카드 payload schema version 및 최대 3페이지 검증
- 책상 object 배치 저장
- Daily / Time Capsule unlock 계산
- unlock된 public 편지 조회
- 카카오 인가 코드 기반 로그인 및 회원 자동 가입
- JWT access token과 회전형 refresh token (HttpOnly 쿠키 지원)
- 로그인 회원 편지 작성 및 닉네임 fallback
- 로그인 사용자의 개인 책상 생성·설정·접수 관리
- 잠금 보호를 포함한 받은 편지 조회·읽음·삭제·바구니 이동
- S3 presigned URL 이미지 업로드 및 완료 검증
- 광고 기간·위치 관리와 노출·시청·클릭 기록, 광고 시청으로 편지지·소품 해제
- 반응·답장·신고·작성자 숨기기, 보낸 응원 목록과 삭제
- 상품 가격표 기반 주문·승인(개발용 mock)과 유료 요소(아크릴 부적·보석) 결제 검증
- 찰떡(충전식 포인트): 토스페이먼츠 충전 승인, 잔액·내역, 구매 시 결제 완료 주문 발급
- 책상 설정 변경 시 아직 열리지 않은 편지의 공개 시각 재계산
- 회원·비회원 트래픽 이벤트 및 운영자 집계

교실 단위 응원은 Post-MVP 범위이며 구현하지 않았습니다.

## Tech

- Java 17
- Spring Boot 4.0.6
- Spring Web MVC / Data JPA
- MySQL, Flyway, H2(test)
- Lombok, springdoc-openapi
- AWS SDK for Java 2.x / S3

## Run

MySQL에 `chak` database를 만든 뒤 실행합니다.

스키마는 Flyway가 관리하며 자동 baseline은 기본적으로 꺼져 있습니다. 빈 DB는 그대로 실행하면 V1부터 적용됩니다.

기존 Hibernate 자동 생성 DB를 처음 전환할 때는 먼저 백업하고 아래 중복 데이터를 확인합니다.

```sql
SELECT owner_id, COUNT(*)
FROM personal_desks
WHERE owner_id IS NOT NULL
GROUP BY owner_id
HAVING COUNT(*) > 1;
```

결과가 없어야 하며, 기존 테이블이 V1 스키마와 일치하는지 확인한 배포에서만 `FLYWAY_BASELINE_ON_MIGRATE=true`를 한 번 사용합니다. 성공 후에는 즉시 `false`로 되돌립니다. V2는 다른 변경보다 먼저 사용자당 개인 책상 하나의 고유 제약을 추가하므로 중복이 있으면 기능 스키마를 건드리기 전에 중단됩니다.

```bash
export DB_URL='jdbc:mysql://localhost:3306/chak?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export DB_USERNAME='root'
export DB_PASSWORD='your-password'
export KAKAO_CLIENT_ID='your-kakao-rest-api-key'
export KAKAO_CLIENT_SECRET='your-client-secret-if-enabled'
export GOOGLE_CLIENT_ID='your-google-oauth-client-id'
export GOOGLE_CLIENT_SECRET='your-google-oauth-client-secret'
export JWT_SECRET='at-least-32-byte-production-secret'
export S3_BUCKET='chak-media'
export AWS_REGION='ap-northeast-2'
# 개발: PAYMENT_PROVIDER 기본값은 mock(실제로 돈이 오가지 않는 가짜 결제)
# 찰떡 충전: CHALTTEOK_PROVIDER=mock(기본)|toss|none, 토스는 TOSS_SECRET_KEY 필요
# 운영: 아래 프로필을 켜면 기본 JWT 시크릿으로는 기동 실패, 쿠키 Secure 기본 true, Swagger 기본 비활성,
#       결제 기본값 none(승인 거부). 실제 PG 연동 전에는 결제를 열지 마세요.
# export SPRING_PROFILES_ACTIVE=prod
export ADMIN_KAKAO_IDS='123456789,987654321'
# export ADMIN_GOOGLE_IDS='google-sub-1,google-sub-2'
./gradlew bootRun
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

상세 요청·응답 및 프론트 연동 규칙: [`docs/API.md`](docs/API.md)
DB 구조: [`docs/DATABASE.md`](docs/DATABASE.md)
배포(AWS, 자동 업데이트): [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)

프론트와 API가 서로 다른 도메인이면 `CORS_ALLOWED_ORIGINS`에 프론트 Origin을 추가하고, 비회원 쿠키와 리프레시 쿠키를 위해 `GUEST_COOKIE_SAME_SITE=None`, `REFRESH_COOKIE_SAME_SITE=None`, `GUEST_COOKIE_SECURE=true`, `REFRESH_COOKIE_SECURE=true`(HTTPS)를 설정합니다. 같은 사이트(서브도메인 포함)면 기본값 `Lax`를 유지합니다.

## Test

```bash
./gradlew test
```

## Public API

```text
GET  /api/public/desks/{supporterToken}
GET  /api/public/desks/{supporterToken}/messages?page=0&size=20
POST /api/public/desks/{supporterToken}/messages
GET  /api/public/health
GET  /api/public/messages/mine
DELETE /api/public/messages/{messageId}
PUT  /api/public/messages/{messageId}/reaction
POST /api/public/messages/{messageId}/report
GET  /api/public/replies/mine
POST /api/public/payments/orders
POST /api/public/unlocks
GET  /api/public/ads?placement=DESK
POST /api/public/ads/{advertisementId}/events
POST /api/public/analytics/events
```

비회원 편지 작성 시 `chak_guest_id` cookie가 없으면 서버가 새 guest identity를 만들고 HttpOnly cookie를 발급합니다.

### Guest message request

```json
{
  "nickname": "민준",
  "kind": "CARD",
  "visibility": "PUBLIC",
  "schemaVersion": 1,
  "card": {"pages": [{"id": "page-1", "elements": []}]},
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

`card`와 `object.metadata`는 frontend renderer와 합의할 versioned JSON입니다. 현재 `schemaVersion=1`, 카드 1~3페이지만 허용합니다.

## Authentication boundary

- 소셜 로그인: `POST /api/auth/kakao`, `POST /api/auth/google`
- 토큰 재발급: `POST /api/auth/refresh`
- 로그아웃: `POST /api/auth/logout`
- 내 정보: `GET /api/users/me`
- 개인 책상 생성·관리: 로그인 필수
- 개인 Supporter link 조회/편지 작성: 비로그인 허용
- 회원 편지: `author_user_id` + 선택적 `nickname_override`
- 비회원 편지: `author_guest_id` + 필수 `nickname_override`
- 표시 이름: `nickname_override` 우선, 없으면 회원의 현재 `display_name`
