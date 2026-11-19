-- 한 번만 보내는 일괄 알림(예: 수능 리캡 오픈)을 이미 보냈는지 기록한다. 서버가 여러 번 떠도 두 번 보내지 않게.
CREATE TABLE push_broadcasts (
    broadcast_key VARCHAR(64) NOT NULL PRIMARY KEY,
    sent_at       DATETIME(6) NOT NULL
);
