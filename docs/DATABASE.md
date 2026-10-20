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
| V4 | `personal_desks.push_enabled` |
| V5 | message_reactions, message_replies, message_reply_targets, message_reports, desk_blocks, 작성자 조회 인덱스 |
| V6 | payment_orders, payment_order_items, ad_unlocks |
| V7 | chaltteok_wallets, chaltteok_charges, chaltteok_ledger |
| V8 | users: `kakao_id` → `provider` + `provider_user_id`, 유일 제약을 (provider, provider_user_id)로 변경 (기존 회원은 모두 KAKAO) |
| V9 | users `last_login_at`·`deleted_at`(탈퇴 익명화), traffic_events `country`·`region`(접속 지역, IP 미저장), admin_audit_logs |

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
| provider | VARCHAR(20) | 로그인 제공자 `KAKAO` `GOOGLE`, 기본 `KAKAO` |
| provider_user_id | VARCHAR(100) | 제공자 식별자(카카오 회원번호, 구글 `sub`). UNIQUE `(provider, provider_user_id)` |
| display_name | VARCHAR(50) | 제공자 닉네임, 로그인마다 갱신 |
| email | VARCHAR(320) | NULL 가능 |
| profile_image_url | VARCHAR(1000) | NULL 가능 |
| admin | BOOLEAN | 기본 FALSE, `ADMIN_KAKAO_IDS`·`ADMIN_GOOGLE_IDS` 로그인 시 갱신 |
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
| push_enabled | BOOLEAN | 새 응원 알림 선호 (V4, 기본 TRUE) |
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

## 커뮤니티 (V5)

### message_reactions
한 사람이 한 편지에 하나의 반응.

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| message_id | BIGINT FK messages | |
| user_id / guest_id | BIGINT FK | 둘 중 하나. UNIQUE `(message_id, user_id)`, `(message_id, guest_id)` |
| reaction_type | VARCHAR(20) | `HEART` `TEARY` `CLAP` |
| created_at, updated_at | DATETIME(6) | |

### message_replies / message_reply_targets
책상 주인의 일방향 답장. 한 답장이 여러 편지(오늘의 응원 전체)를 대상으로 할 수 있고, 편지당 답장은 하나다.

| 테이블 | 컬럼 | 비고 |
| --- | --- | --- |
| message_replies | id, desk_id FK, owner_user_id FK, scope(`SINGLE`/`DAILY`), source_message_id FK(NULL 가능), reply_text(≤500), created_at | |
| message_reply_targets | id, reply_id FK, message_id FK | UNIQUE `message_id` (편지당 답장 1개) |

### message_reports
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| message_id | BIGINT FK | |
| reporter_user_id / reporter_guest_id | BIGINT FK | 둘 중 하나. UNIQUE `(message_id, reporter_*)` |
| reason | VARCHAR(100) | NULL 가능 |
| status | VARCHAR(20) | `OPEN` `RESOLVED` `DISMISSED` |
| created_at, resolved_at | DATETIME(6) | 인덱스 `(status, created_at)` |

### desk_blocks
책상 주인이 숨긴 작성자.

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | API의 `blockId` |
| desk_id | BIGINT FK | |
| author_user_id / author_guest_id | BIGINT FK | 둘 중 하나. UNIQUE `(desk_id, author_*)` |
| display_name | VARCHAR(50) | 숨길 때의 표시 이름 |
| created_at | DATETIME(6) | |

## 결제·해제 (V6)

### payment_orders / payment_order_items
| 테이블 | 컬럼 | 비고 |
| --- | --- | --- |
| payment_orders | id, payer_user_id / payer_guest_id, total_amount(원), status(`CREATED` `PAID` `CANCELED`), provider(`MOCK` `NONE` …), provider_ref, consumed_at, created_at, paid_at | `consumed_at`이 있으면 이미 사용한 주문 |
| payment_order_items | id, order_id FK, item_code(ProductCode), item_ref, quantity, unit_price, amount | 단가는 서버 `ProductCode` 기준으로 주문 시점 값을 저장 |

### ad_unlocks
| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT PK | |
| user_id / guest_id | BIGINT FK | 둘 중 하나. UNIQUE `(user_id, category, asset_id)`, `(guest_id, category, asset_id)` |
| category | VARCHAR(20) | `STATIONERY` `DECOR` |
| asset_id | VARCHAR(100) | 프론트 에셋 식별자 |
| ad_session_id | VARCHAR(100) | 해제에 쓴 광고 노출 식별자 |
| created_at | DATETIME(6) | |

## 찰떡 포인트 (V7)

충전식 포인트(1원 = 1찰떡)이며 회원 계정 단위입니다. 비회원은 쓸 수 없습니다. 돈과 직결되므로 **불변 조건을 애플리케이션뿐 아니라 DB 제약으로도 강제**합니다.

| 테이블 | 컬럼 | 제약·역할 |
| --- | --- | --- |
| chaltteok_wallets | id, user_id, balance, customer_key, version, created_at, updated_at | `user_id` UNIQUE(회원당 지갑 1개), `customer_key` UNIQUE(무작위 UUID, 토스 구매자 식별자), **CHECK balance ≥ 0**. `version`은 잠금 없이 쓰는 실수를 잡는 낙관적 락 안전망 |
| chaltteok_charges | id, user_id, order_id, amount, status(`READY` `CONFIRMING` `DONE`), payment_key, result_balance, claimed_at, created_at, confirmed_at | `order_id` UNIQUE, **`payment_key` UNIQUE**(한 결제를 두 주문에 못 씀), CHECK amount 300~50,000, **CHECK DONE이면 payment_key·result_balance·confirmed_at 필수** |
| chaltteok_ledger | id, user_id, entry_type(`CHARGE` `PURCHASE`), amount(충전 +, 구매 −), balance_after, ref, idempotency_key, request_hash, created_at | append-only. **UNIQUE (entry_type, ref)**: 충전 주문·구매 주문은 장부에 한 번만. **UNIQUE (user_id, idempotency_key)**: 같은 키로 이중 차감 불가. CHECK 부호(충전>0, 구매<0), CHECK balance_after ≥ 0 |

- 장부 합계(`SUM(amount)`)는 항상 지갑 `balance`와 같아야 합니다(`ChaltteokService.isConsistent`, 테스트가 검증).
- `status` 값 목록 CHECK는 넣지 않았습니다(테스트용 H2 2.4의 `IN` 제약 버그). 값은 enum과 선점 쿼리로만 바뀝니다.
- 구매하면 `payment_orders`에 `provider='CHALTTEOK'`, `status='PAID'` 주문이 만들어지고, 편지 전송 때 `paymentOrderId`로 한 번 소비됩니다.

### 동시성 설계
| 상황 | 방어 |
| --- | --- |
| 동시 구매로 잔액 초과 차감 | 지갑 행 `SELECT … FOR UPDATE` 후 잔액 확인·차감. 잔액 100에서 30원 구매를 동시에 20번 요청하면 정확히 3건만 성공(테스트) |
| 같은 충전 주문을 동시에 승인 | `UPDATE … WHERE status='READY'` 선점(CAS)으로 한 요청만 토스를 호출, 나머지는 `409 CHARGE_IN_PROGRESS`. 선점은 2분 뒤 만료 |
| 토스 호출이 느림 | 토스 호출은 **트랜잭션 밖**(DB 잠금·커넥션을 쥐지 않음), 연결 3초·읽기 15초 타임아웃 |
| 토스 승인 후 DB 반영 실패 | 재시도 시 토스가 `ALREADY_PROCESSED_PAYMENT`를 돌려주면 결제를 조회해 DONE·주문번호·금액이 일치할 때만 인정 후 반영 |
| 구매 더블클릭·재시도 | `Idempotency-Key`로 처음 결과 재응답. 같은 키에 다른 내용이면 `409` |
| 지갑 최초 생성 경합 | `INSERT IGNORE`(유니크 제약 위에서 예외 없이 1건으로 합쳐짐) |
| 결제 안 끝난 주문 폭주 | 사용자당 최근 1시간 `READY` 주문 10개 상한. 지갑 행 잠금으로 직렬화해 우회 불가 |
| 데드락 | 잠금 순서를 충전 주문 → 지갑으로 고정(구매는 지갑만) |

## 어드민·통계 (V9)

- `users.deleted_at`: 값이 있으면 탈퇴 처리된 익명 계정입니다. 개인정보(이름·이메일·프로필)를 지우고 `provider_user_id`를 `deleted:{id}`로 바꿔 같은 로그인으로 재가입하면 새 계정이 됩니다. 행은 응원·결제 장부의 FK 때문에 남습니다.
- `users.last_login_at`: 마지막 로그인 시각(관리자 화면용).
- `traffic_events.country`, `region`: 접속 국가(ISO 2자리)와 시·도 코드. 호스팅이 알려주는 값만 저장하고 IP는 저장하지 않습니다. 인덱스 `(event_type, created_at, region)`.
- `admin_audit_logs`: 관리자 작업 기록(`admin_user_id`, `action`, `target_type`, `target_id`, `detail`, `created_at`).
