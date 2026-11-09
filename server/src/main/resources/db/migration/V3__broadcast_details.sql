-- D5: 방송 설명 + 스트림 키(MediaMTX 경로 live/<stream_key>, HLS 주소에 쓰임)
ALTER TABLE broadcasts ADD COLUMN description VARCHAR(1000);
ALTER TABLE broadcasts ADD COLUMN stream_key VARCHAR(32);
UPDATE broadcasts SET stream_key = substr(md5(random()::text || id::text), 1, 16) WHERE stream_key IS NULL;
ALTER TABLE broadcasts ALTER COLUMN stream_key SET NOT NULL;
ALTER TABLE broadcasts ADD CONSTRAINT uq_broadcasts_stream_key UNIQUE (stream_key);
CREATE INDEX idx_broadcasts_status ON broadcasts (status);
