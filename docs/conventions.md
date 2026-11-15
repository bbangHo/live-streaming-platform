# conventions.md — 코드 쓰기 전에 읽는 금지목록

하위 세션은 코드 쓰기 전에 이 파일을 읽는다. AI가 자주 저지르는 실수를 미리 막아 리뷰 없이도 품질을 유지한다. 스택: Spring Boot(Java) · JPA · PostgreSQL · MediaMTX · 프론트(정적/React TBD).

| 분류 | 하지 않는 것 |
|---|---|
| **공통** | 테스트를 코드에 맞춰 고쳐서 통과시키기 · 시나리오 테스트에서 DB mock(진짜 Postgres, mock은 외부연동 fake만) · 예외 삼키기(빈 catch, 무시) · 기능 PR에 무관한 리팩터링 섞기 · 정의 안 된 엔드포인트 몰래 추가 · `TODO`만 남기기(`shortcut:`으로) · 디버그 로그 남기기 · 적용된 마이그레이션 수정 · 몇 줄 때문에 의존성 추가 |
| **보안·돈** | 클라이언트가 보낸 금액·대상·사용자 id 믿기 · 프론트에서만 권한 확인 · 토큰을 localStorage에(httpOnly 쿠키 또는 메모리) · 돈 화면 낙관적 업데이트 · SQL에 사용자 입력 이어붙이기(PreparedStatement/JPA 바인딩) · 비밀번호 평문·약한 해시(BCrypt/Argon2) |
| **Spring·JPA** | 컨트롤러에 비즈니스 로직 · 엔티티를 그대로 API로 노출(DTO 쓰기) · N+1 방치 · `@Transactional` 없이 다중 쓰기 · 잔액은 읽고-쓰기 말고 조건부 UPDATE(영향행 확인) · 예외를 그대로 500으로(핸들러에서 매핑) · 비밀값이 로그/응답에 |
| **프론트·WebRTC** | 화면공유·카메라를 사용자 클릭 밖에서 시작 · 언마운트 때 미디어 트랙·연결 정리 안 하기 · 소리 있는 자동재생 · 페이지를 `file://`로 열기(http로 서빙, Origin null이면 CORS 실패) · 색·간격 하드코딩 · 상태 관리 없이 직접 fetch 남발 |

**관례:** JSON camelCase · id·금액은 정수형(부동소수 금지) · 시간 UTC 저장·KST 표시 · 에러 `{error:{code,message}}` · 민감정보 `.env`(커밋 금지).

**이미 겪은 것(지키기):** 송출 페이지는 반드시 `http://localhost:PORT`로 서빙(= `docker compose`의 nginx). `file://`로 열면 WHIP이 `Failed to fetch`.
