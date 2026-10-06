# CHAK 데이터베이스 명세

- DB: MySQL 8 (테스트는 H2)
- 스키마 관리: Flyway (`src/main/resources/db/migration`), 운영은 `ddl-auto=validate`
- 시간 컬럼: `DATETIME(6)`, 애플리케이션이 UTC로 저장 (`hibernate.jdbc.time_zone=UTC`)
- 열거형은 모두 `VARCHAR`에 문자열로 저장

| 마이그레이션 | 내용 |
| --- | --- |
| V1 | 기본 스키마 (users, guest_identities, personal_desks, messages, personal_message_deliveries, desk_objects, refresh_tokens) |
| V2 | `personal_desks.owner_id` 유니크 (계정당 개인 책상 1개) |
| V3 | `users.admin`, `personal_desks.default_message_visibility`, media_assets, advertisements, advertisement_events, traffic_events, 배달 인덱스 |

## 관계도

```text
users 1 ──── 0..1 personal_desks (owner_id, UNIQUE)
users 1 ──── N    personal_desks (creator_id)
users 1 ──── N    refresh_tokens
users 1 ──── N    media_assets
users / guest_identities ── N messages (둘 중 하나만 채움)
messages 1 ──── 1 personal_message_deliveries ── N:1 personal_desks
messages 1 ──── 1 desk_objects ── N:1 personal_desks
advertisements 1 ──── N advertisement_events (user 또는 guest)
traffic_events (user 또는 guest, FK 없는 resource_key)
```

## 테이블

### users
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| kakao_id | VARCHAR(100) | UNIQUE, 카카오 회원번호 |
| display_name | VARCHAR(50) | 카카오 닉네임, 로그인마다 갱신 |
| email | VARCHAR(320) | NULL 가능 |
| profile_image_url | VARCHAR(1000) | NULL 가능 |
| admin | BOOLEAN | 기본 FALSE, `ADMIN_KAKAO_IDS` 로그인 시 갱신 |
| created_at, updated_at | DATETIME(6) | |

### guest_identities
비회원 식별자. `chak_guest_id` 쿠키 값이 `guest_key`.

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| guest_key | VARCHAR(36) | UNIQUE, UUID |
| created_at | DATETIME(6) | |

### personal_desks
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| owner_id | BIGINT FK users | UNIQUE, 계정당 1개 |
| creator_id | BIGINT FK users | NOT NULL |
| display_name | VARCHAR(50) | |
| claim_status | VARCHAR(20) | `UNCLAIMED` / `CLAIMED` (API 미노출) |
| read_mode_type | VARCHAR(20) | `DAILY` / `TIME_CAPSULE` |
| daily_unlock_time | TIME(6) | DAILY일 때 필수 |
| capsule_unlock_at | DATETIME(6) | TIME_CAPSULE일 때 필수 |
| timezone | VARCHAR(40) | DAILY 계산 기준 (기본 Asia/Seoul) |
| public_feed_enabled | BOOLEAN | 공개 편지 목록 허용 |
| default_message_visibility | VARCHAR(20) | `PUBLIC` / `PRIVATE`, 기본 PRIVATE |
| room_closed | BOOLEAN | 접수 종료 |
| supporter_token | VARCHAR(36) | UNIQUE, 공유 링크 토큰(UUID) |
| created_at, updated_at | DATETIME(6) | |

### messages
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| author_user_id | BIGINT FK users | 회원 작성 시 |
| author_guest_id | BIGINT FK guest_identities | 비회원 작성 시 |
| nickname_override | VARCHAR(50) | 비회원 필수, 회원 선택 |
| kind | VARCHAR(20) | `CARD` / `STICKER` |
| visibility | VARCHAR(20) | `PUBLIC` / `PRIVATE` |
| status | VARCHAR(20) | `SENT` / `READ` / `DELETED` |
| schema_version | INTEGER | 현재 1 |
| card_payload | LONGTEXT | 카드 JSON 문자열 (최대 500,000자) |
| created_at, updated_at | DATETIME(6) | |
| read_at, deleted_at | DATETIME(6) | soft delete |

작성자 표시명: `nickname_override` → 회원 `users.display_name` → 익명.

### personal_message_deliveries
편지가 어느 책상에 언제 열리는지.

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | API의 `deliveryId` |
| message_id | BIGINT FK messages | UNIQUE |
| desk_id | BIGINT FK personal_desks | |
| unlock_at | DATETIME(6) | 공개 시각 |
| preclaim_backlog | BOOLEAN | 예약 필드 |
| basketed_at | DATETIME(6) | 바구니로 옮긴 시각 (`POST /api/desks/me/messages/basket`) |
| created_at | DATETIME(6) | |

인덱스: `(desk_id, created_at)`, `(desk_id, unlock_at)`

### desk_objects
책상 위 편지 오브젝트 배치 (편지당 1개).

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| desk_id | BIGINT FK | |
| message_id | BIGINT FK | UNIQUE |
| representation_type | VARCHAR(30) | `MEMO` `PHOTO_CARD` `CHARM` `POSTER_CARD` `LETTER` `TICKET` `GENERIC_CARD` `STICKER` |
| asset_id | VARCHAR(100) | 프론트 에셋 식별자 (S3 media_assets와 무관) |
| color | VARCHAR(30) | |
| x, y | DOUBLE | 0~100 (%) |
| rotation | DOUBLE | 도 단위 |
| scale | DOUBLE | 0 초과 3 이하 |
| z_index | INTEGER | |
| metadata_payload | LONGTEXT | 자유 JSON |
| created_at | DATETIME(6) | |

### refresh_tokens
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| user_id | BIGINT FK users | |
| token_hash | VARCHAR(64) | UNIQUE, 원문 미저장 (SHA-256) |
| expires_at | DATETIME(6) | |
| created_at | DATETIME(6) | |
| revoked_at | DATETIME(6) | 회전·로그아웃 시 설정 |

### media_assets
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | API의 `assetId` |
| owner_id | BIGINT FK users | |
| purpose | VARCHAR(20) | `PROFILE` / `CARD` |
| status | VARCHAR(20) | `PENDING` / `UPLOADED` |
| object_key | VARCHAR(500) | UNIQUE, S3 키 (응답 미노출) |
| original_file_name | VARCHAR(255) | |
| content_type | VARCHAR(100) | jpeg/png/webp/gif |
| expected_size | BIGINT | 바이트, 프로필 ≤5MB / 카드 ≤10MB |
| created_at, updated_at, uploaded_at | DATETIME(6) | |

### advertisements
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| title | VARCHAR(100) | |
| creative_url | VARCHAR(1000) | 광고 이미지 URL |
| destination_url | VARCHAR(1000) | 클릭 이동 URL |
| placement | VARCHAR(30) | `HOME` `DESK` `MESSAGE_COMPLETE` |
| starts_at, ends_at | DATETIME(6) | 노출 기간 |
| active | BOOLEAN | DELETE API는 FALSE로 변경 |
| created_at, updated_at | DATETIME(6) | |

### advertisement_events
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| advertisement_id | BIGINT FK | |
| user_id / guest_id | BIGINT FK | 둘 중 하나 |
| event_type | VARCHAR(30) | `IMPRESSION` `VIEW_STARTED` `VIEW_COMPLETED` `CLICK` |
| session_id | VARCHAR(100) | 노출 단위 식별자 |
| view_duration_ms | BIGINT | |
| created_at | DATETIME(6) | |

UNIQUE `(advertisement_id, event_type, session_id)` — 재시도 중복 방지.
인덱스: `(advertisement_id, event_type, created_at)`, `(user_id, created_at)`, `(guest_id, created_at)`

### traffic_events
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| event_id | VARCHAR(64) | UNIQUE, 클라이언트/서버 생성 고유 ID |
| user_id / guest_id | BIGINT FK | 둘 중 하나 |
| event_type | VARCHAR(40) | `PAGE_VIEW` `INVITE_LINK_OPEN` `MESSAGE_COMPOSE_STARTED` `MESSAGE_CREATED` |
| session_id | VARCHAR(100) | |
| path, referrer | VARCHAR(1000) | |
| resource_type | VARCHAR(100) | 예: `PERSONAL_DESK` |
| resource_key | VARCHAR(200) | 예: supporterToken |
| metadata_payload | LONGTEXT | 자유 JSON |
| created_at | DATETIME(6) | |

인덱스: `(event_type, created_at)`, `(user_id, created_at)`, `(guest_id, created_at)`

원문 IP와 User-Agent는 저장하지 않는다.
