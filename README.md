# live-streaming-platform

치지직 같은 실시간 라이브 방송 송출·시청 웹 서비스. (Codemit 공모전 제출작)

> 상태: **개발 중** · 마감 2026-11-19 23:59 · 평가 기준·계획은 [PLAN.md](./PLAN.md) 참고
> 현재 구현: 브라우저 화면 송출(WHIP) → MediaMTX → HLS 시청, 백엔드 회원가입·로그인(JWT)·소유자 인가(API). 방송 관리 화면·모바일 송출은 개발 중.

---

## 심사자용 안내

- **서비스 주소:** 추후(제출 임박) 결정. 지금은 아래 **로컬 실행**으로 확인
- **테스트 계정:** `demo1` / `demo1234`, `demo2` / `demo1234` (DB 첫 기동 때 자동 생성. `demo1`이 시연 방송 1개를 가짐 → `demo2`로 수정하면 403). 새 계정은 `POST /api/auth/signup`으로 직접 가입 가능 — 아래 [인증·인가 확인](#인증인가-확인-api)
- 시연 영상(선택): TODO

## 실행 방법

### 실행 환경
| 항목 | 버전 / 비고 |
|---|---|
| Docker + Docker Compose | Docker Desktop 4.x 이상 (Compose v2) |
| 브라우저 (송출) | **Chrome / Edge 최신** — 화면 + 탭/시스템 소리 공유 지원 |
| 브라우저 (시청) | Chrome / Edge / Firefox 최신 (hls.js). Safari는 네이티브 HLS — 미확인 |
| Node.js (E2E만) | 18 이상 |

확인한 환경: Windows 11, Docker 29.8, Edge 최신, MediaMTX 1.21.3.

### 1. 전체 기동 (한 줄)
```bash
docker compose up -d
```
| 주소 | 용도 |
|---|---|
| http://localhost:8000/publish.html | 송출 페이지 |
| http://localhost:8000/watch.html | 시청 페이지 |
| `:8889` / `:8888` / `:8189` (UDP·TCP) | MediaMTX — WHIP / HLS / WebRTC 미디어 |
| http://localhost:8080/api | 백엔드 API (Spring Boot) — 인증/인가, 방송 정보 |
| (내부) postgres | DB. `pgdata` 볼륨에 저장 → 재시작·`down` 후에도 유지 (`down -v`만 삭제) |

처음 실행은 백엔드 이미지 빌드(Gradle 의존성 내려받기) 때문에 몇 분 걸립니다.

종료: `docker compose down`

> ⚠️ **페이지를 `file://`로(파일 더블클릭) 열지 마세요.** Origin이 `null`이 되어 MediaMTX가 CORS 허용 헤더를 주지 않고, 송출이 `Failed to fetch`로 실패합니다. 반드시 위의 `http://localhost:8000/...` 주소로 엽니다. (`localhost`는 HTTPS가 아니어도 화면·마이크 캡처가 허용되는 secure context입니다.)

### 2. 환경변수 (선택)
없어도 PC 한 대에서는 그대로 동작합니다. 바꿀 때만 `cp .env.example .env` 후 수정 → [.env.example](./.env.example)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `WEB_PORT` | `8000` | 페이지 포트 |
| `PUBLIC_HOST` | `127.0.0.1` | WebRTC가 알려 줄 주소. 다른 기기에서 송출하면 이 PC의 LAN IP |
| `DOMAIN` | `localhost` | `--profile https`에서 인증서 낼 이름 |
| `API_PORT` | `8080` | 백엔드 API 포트 (8080을 다른 프로그램이 쓰면 바꿈) |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `livestream` / `livestream` / 로컬 기본값 | DB 자격증명. 기본값은 로컬 전용(DB 포트는 외부에 안 엶) — **배포 시 반드시 .env로 지정** |
| `JWT_SECRET` | (비어 있음) | access token 서명 키, 32자 이상. 비우면 기동마다 임의 키(재시작하면 다시 로그인) |

### Docker 없이 실행
MediaMTX 바이너리를 받아 직접 띄웁니다 → [media/README.md](./media/README.md)
```bash
media/mediamtx.exe media/mediamtx.yml
python -m http.server 8000 --bind 127.0.0.1 -d web
```

## 기능 확인

### PC 송출 → 시청 → 종료
1. 소리가 나는 탭을 하나 띄웁니다 (예: 유튜브).
2. **송출:** http://localhost:8000/publish.html → 스트림 키(기본 `test`) → **방송 시작** → 공유 창에서 그 탭 선택 + **「오디오 공유」 켜기** → 마이크 허용.
   로그에 `화면 소리 1개, 마이크 1개`, `WHIP 201`, 상태 `connected`가 뜹니다.
3. **시청:** 다른 창에서 http://localhost:8000/watch.html → 같은 스트림 키 → **재생** → 영상 컨트롤에서 음소거 해제.
   1~4초 지연으로 화면과 **탭 소리 + 마이크 소리**가 나옵니다.
4. **종료:** 송출 페이지의 **방송 종료** 또는 브라우저의 **「공유 중지」**. 송출 상태가 `종료`로 바뀌고 시청 화면이 멈춥니다.

### 모바일 시청
같은 와이파이의 휴대폰에서 `http://<이 PC의 LAN IP>:8000/watch.html` → 재생. (시청은 HTTPS가 필요 없음. Windows 방화벽이 8000·8888 포트를 막으면 허용 필요 — **아직 실기기 미확인**)

### 모바일 송출 (개발 중 — D8)
모바일 카메라 송출 페이지는 아직 없습니다. 휴대폰 브라우저는 `localhost`가 아니면 HTTPS에서만 카메라를 열 수 있어, 그때는 HTTPS 프로필을 씁니다:
```bash
# .env: DOMAIN=<PC LAN IP>, PUBLIC_HOST=<PC LAN IP>
docker compose --profile https up -d   # → https://<PC LAN IP>/  (사설 인증서 경고는 수락)
```

### 인증·인가 확인 (API)
화면은 D5~에서 붙습니다. 지금은 API로 확인합니다 (Git Bash / macOS / Linux 기준):
```bash
B=http://localhost:8080/api
# 1) 비로그인: 목록 조회는 200, 등록은 401
curl -i $B/broadcasts
curl -i -X POST $B/broadcasts -H 'Content-Type: application/json' -d '{"title":"x"}'
# 2) 테스트 계정 로그인 → accessToken, refreshToken
curl -s -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"username":"demo2","password":"demo1234"}'
# 3) demo2가 demo1의 방송(1번) 수정 → 403 "방송 소유자만 할 수 있음"
curl -i -X PATCH $B/broadcasts/1 -H "Authorization: Bearer <demo2 accessToken>" -H 'Content-Type: application/json' -d '{"title":"hack"}'
```

| 메서드 · 경로 | 권한 | 설명 |
|---|---|---|
| `POST /api/auth/signup` | 공개 | `{username, password}` 가입 (아이디 영문·숫자·_ 3~30자, 비번 8~72자, BCrypt 저장) → 201 |
| `POST /api/auth/login` | 공개 | → `{accessToken, refreshToken, tokenType, expiresIn}`. 틀리면 401 |
| `POST /api/auth/refresh` | 공개 | `{refreshToken}` → 새 토큰 쌍. 쓴 refresh token은 폐기(회전) |
| `POST /api/auth/logout` | 공개 | `{refreshToken}` 폐기 → 204. access token은 남은 수명(15분)까지만 유효 |
| `GET /api/auth/me` | 로그인 | 내 정보 |
| `GET /api/broadcasts`, `GET /api/broadcasts/{id}` | 공개 | 방송 목록·상세 |
| `POST /api/broadcasts` | 로그인 | `{title}` 방송 등록 (등록자 = 소유자) |
| `PATCH /api/broadcasts/{id}`, `DELETE /api/broadcasts/{id}` | **소유자** | 수정·삭제. 타인 403, 비로그인 401 |
| `POST /api/broadcasts/{id}/start`, `/stop` | **소유자** | 송출 상태 LIVE / ENDED |

인가는 전부 서버에서 합니다: 로그인 여부는 [SecurityConfig](./server/src/main/java/com/livestream/config/SecurityConfig.java), 소유자는 [BroadcastService.requireOwner](./server/src/main/java/com/livestream/broadcast/BroadcastService.java). 오류 응답은 `{"status":403,"detail":"..."}` 형식(RFC 9457).

### 백엔드 테스트
```bash
cd server
./gradlew test
```
- JDK 21 + **Docker 필요** (Testcontainers가 실제 PostgreSQL을 띄워 Flyway 스키마로 돌립니다).
- 검증 범위: 가입·로그인·내정보·재발급(회전)·로그아웃, 중복 아이디 409, 잘못된 입력 400, 틀린 비번/없는 아이디 401, 위조·만료 토큰 거부, 테스트 계정 로그인, 방송 인가 **비로그인 401 / 타인 403(데이터 불변) / 소유자 허용**.
- ⚠️ Windows에서 프로젝트 경로에 **한글**이 있으면 Gradle 테스트 워커가 `ClassNotFoundException`으로 실패합니다. 영문 경로에 두거나 `./gradlew test -Dorg.gradle.jvmargs=-Dfile.encoding=MS949`로 실행하세요.

### 자동 E2E 테스트
```bash
npm install
npm run test:e2e
```
- 설치된 Edge로 **진짜 탭을 캡처**해 송출 → HLS 시청까지 돌리고, 시청 쪽 소리를 주파수 분석해 **탭 소리와 마이크가 둘 다 섞여 오는지**, 영상 속 시계로 **영상·지연**, 탭을 닫으면 **자동 종료**되는지 판정합니다. (마이크는 1000Hz 발진기로 대신)
- `docker compose up -d` 상태면 그 스택을 쓰고, 아니면 MediaMTX 바이너리·python 서버를 직접 띄웁니다.
- 테스트 중 스피커에서 삐 소리가 납니다.
- 다른 주소 대상: `E2E_BASE=https://<도메인> npm run test:e2e`

자세한 검증 범위와 한계: [docs/spike-d1-streaming.md](./docs/spike-d1-streaming.md)

## 기술 스택

- 미디어: **MediaMTX** (WHIP 수신 → HLS/WHEP 출력) · 브라우저 WebRTC · hls.js
- 백엔드: **Spring Boot 4 (Java 21)** · Spring Security · BCrypt · JWT(jjwt) · Spring Data JPA · Flyway → [server/](./server)
- DB: **PostgreSQL 17**
- 로컬 실행: Docker Compose (MediaMTX + nginx + backend + postgres, 선택 Caddy HTTPS)
- 테스트: Playwright (E2E), JUnit 5 + MockMvc + Testcontainers (백엔드)
- 프론트: [PLAN.md](./PLAN.md)의 "기술 결정" 참고
- 문서: [docs/streaming-tech-reference.md](./docs/streaming-tech-reference.md)

## 출처·라이선스

- [MediaMTX](https://github.com/bluenviron/mediamtx) (MIT) — 미디어 서버, Docker 이미지·바이너리로 사용 (레포에 포함하지 않음)
- [hls.js](https://github.com/video-dev/hls.js) (Apache-2.0) — CDN으로 로드
- [Caddy](https://caddyserver.com) (Apache-2.0), [nginx](https://nginx.org) (BSD-2) — Docker 이미지
- [Playwright](https://playwright.dev) (Apache-2.0) — 개발 의존성
- [Spring Boot](https://spring.io/projects/spring-boot) · Spring Security (Apache-2.0), [Hibernate ORM](https://hibernate.org) (Apache-2.0), [Flyway](https://github.com/flyway/flyway) (Apache-2.0) — 백엔드
- [JJWT](https://github.com/jwtk/jjwt) (Apache-2.0) — JWT 발급·검증
- [PostgreSQL](https://www.postgresql.org) (PostgreSQL License) — Docker 이미지, JDBC 드라이버(BSD-2)
- [Testcontainers](https://testcontainers.com) (MIT) — 백엔드 테스트
