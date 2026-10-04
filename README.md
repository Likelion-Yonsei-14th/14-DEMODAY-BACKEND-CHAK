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

로그인, Claim, Owner API, 사진 Object Storage, 광고, 교실은 아직 구현하지 않았습니다. 교실은 Post-MVP 범위입니다.

## Tech

- Java 17
- Spring Boot 4.0.6
- Spring Web MVC / Data JPA
- MySQL, H2(test)
- Lombok, springdoc-openapi

## Run

MySQL에 `chak` database를 만든 뒤 실행합니다.

```bash
export DB_URL='jdbc:mysql://localhost:3306/chak?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export DB_USERNAME='root'
export DB_PASSWORD='your-password'
./gradlew bootRun
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Test

```bash
./gradlew test
```

## Public API

```text
GET  /api/public/desks/{supporterToken}
GET  /api/public/desks/{supporterToken}/messages
POST /api/public/desks/{supporterToken}/messages
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

로그인 구현은 후속 단계지만 domain은 회원 연결을 고려해 설계했습니다.

- 개인 책상 생성: 로그인 필수 예정
- 개인 Supporter link 조회/편지 작성: 비로그인 허용
- 회원 편지: `author_user_id` + 선택적 `nickname_override`
- 비회원 편지: `author_guest_id` + 필수 `nickname_override`
- 표시 이름: `nickname_override` 우선, 없으면 회원의 현재 `display_name`
