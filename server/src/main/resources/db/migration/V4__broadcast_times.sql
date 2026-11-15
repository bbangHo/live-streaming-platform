-- D6: 송출이 실제로 시작·끝난 시각 (MediaMTX 상태 동기화가 채운다). 녹화·다시보기 목록에도 쓴다
ALTER TABLE broadcasts ADD COLUMN started_at TIMESTAMPTZ;
ALTER TABLE broadcasts ADD COLUMN ended_at   TIMESTAMPTZ;
