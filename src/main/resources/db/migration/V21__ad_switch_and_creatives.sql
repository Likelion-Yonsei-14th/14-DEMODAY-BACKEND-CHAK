-- 운영 설정 한 줄씩. 지금은 광고 전체 노출 스위치(ads.enabled) 하나만 쓴다.
CREATE TABLE app_settings (
    setting_key VARCHAR(60) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    PRIMARY KEY (setting_key)
);

-- 광고는 수능날 한 번에 연다. 그 전까지는 등록해 둬도 사용자에게 보이지 않는다.
INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('ads.enabled', 'false', CURRENT_TIMESTAMP(6));

-- 운영자가 올린 광고 이미지. 한 번 올린 이미지는 바뀌지 않으므로 id로 오래 캐시한다.
CREATE TABLE ad_creatives (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_type VARCHAR(30) NOT NULL,
    byte_size INT NOT NULL,
    data LONGBLOB NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
