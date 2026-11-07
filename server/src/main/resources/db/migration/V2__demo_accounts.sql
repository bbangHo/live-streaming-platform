-- 심사·시연용 테스트 계정 (README "테스트 계정"). 비밀번호 demo1234 의 BCrypt 해시 — 공개용 시연 값이라 커밋한다.
-- demo2로 demo1의 방송을 수정하면 403(소유자 검증)을 바로 확인할 수 있다.
INSERT INTO users (username, password_hash) VALUES
    ('demo1', '$2a$10$Tv0aNUNj2Pc2SR3ZxCxVLubtEMeld6Ub8que3Ud4F0f5NSdoDU63i'),
    ('demo2', '$2a$10$Tv0aNUNj2Pc2SR3ZxCxVLubtEMeld6Ub8que3Ud4F0f5NSdoDU63i');

INSERT INTO broadcasts (owner_id, title)
SELECT id, 'demo1의 시연 방송' FROM users WHERE username = 'demo1';
