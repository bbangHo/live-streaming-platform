-- 선택 기능: 녹화. MediaMTX가 녹화 파일(세그먼트)을 완성할 때마다 위치·시작 시각·길이를 영구 저장한다.
-- 파일 자체는 MediaMTX recordings 볼륨에 있고, 재생은 MediaMTX playback 서버(/playback/get)가 한다
CREATE TABLE recordings (
    id           BIGSERIAL PRIMARY KEY,
    broadcast_id BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    stream_path  VARCHAR(100) NOT NULL,          -- MediaMTX 경로 live/<스트림 키>
    file_path    VARCHAR(500) NOT NULL UNIQUE,   -- 녹화 파일 위치 (같은 알림이 두 번 와도 한 행)
    started_at   TIMESTAMPTZ  NOT NULL,
    duration_ms  BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_recordings_broadcast ON recordings (broadcast_id, started_at);
