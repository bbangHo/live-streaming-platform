# live-streaming-platform

치지직 같은 실시간 라이브 방송 송출·시청 웹 서비스. (Codemit 공모전 제출작)

> 상태: **개발 중** · 마감 2026-11-19 23:59 · 평가 기준·계획은 [PLAN.md](./PLAN.md) 참고
> 현재 구현: 브라우저 화면 송출(WHIP) → MediaMTX → HLS 시청, 회원가입·로그인(JWT)·소유자 인가, 방송 등록·목록·상세(시청)·수정·삭제 화면, 송출 페이지 ↔ 방송 상태 연동(방송 시작 = WHIP 송출 + LIVE, 종료 = 송출 끊기 + ENDED, 소유자만 송출). 모바일 송출은 개발 중.

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
| **http://localhost:8000/** | **방송 목록** (여기서 시작) — 로그인·방송 만들기·상세(시청)·관리 |
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
> 이 방식은 백엔드가 없어 로그인·방송 관리·송출 페이지(로그인 필요)가 동작하지 않습니다. 전체 기능은 `docker compose up -d`로 확인하세요.

## 기능 확인

### 방송 관리 (화면)
1. http://localhost:8000/ → 우측 상단 **로그인** → 테스트 계정 `demo1` / `demo1234` (또는 회원가입).
2. **방송 만들기**에 제목·설명 입력 → 상세 페이지로 이동. 소유자에게만 **방송 관리**(송출 페이지에서 방송 시작·종료·삭제·수정, 스트림 키)가 보입니다.
3. **송출 페이지에서 방송 시작** → 그 방송이 선택된 송출 페이지에서 **방송 시작**(아래 PC 송출 절차). 화면 공유가 연결되면 상태가 자동으로 `방송 중`이 되고, 비로그인 시청자도 상세 페이지에서 영상이 재생됩니다.
4. 로그아웃하거나 `demo2`로 로그인해 같은 방송을 열면 관리 영역이 없고, API로 직접 바꿔도 401/403입니다.
5. `docker compose restart` 또는 `down` → `up` 후에도 방송이 그대로 조회됩니다(PostgreSQL 볼륨).

### PC 송출 → 시청 → 종료
1. 소리가 나는 탭을 하나 띄웁니다 (예: 유튜브).
2. **송출:** http://localhost:8000/publish.html (로그인 필요 — 안 했으면 로그인 화면으로 갑니다) → 내 방송 선택(대기·방송 중인 것만) 또는 **새 방송 만들기** → **방송 시작** → 공유 창에서 그 탭 선택 + **「오디오 공유」 켜기** → 마이크 허용.
   로그에 `화면 소리 1개, 마이크 1개`, `WHIP 201`, `방송 시작 … 상태 LIVE`, 상태 `connected`가 뜹니다. 목록의 **방송 중** 탭에 나타납니다.
3. **시청:** 다른 창(비로그인 가능)에서 목록 → 그 방송 상세 → 영상 컨트롤에서 음소거 해제.
   1~4초 지연으로 화면과 **탭 소리 + 마이크 소리**가 나옵니다. (디버그용 http://localhost:8000/watch.html 에 스트림 키를 넣어도 됩니다)
4. **종료:** 송출 페이지의 **방송 종료**, 브라우저의 **「공유 중지」**, 송출 탭 닫기 중 아무거나 → WHIP 송출이 끊기고 상태가 `종료`(ENDED)가 됩니다. 상세 페이지의 **방송 종료**로 끝내도 열린 송출 페이지가 5초 안에 송출을 끊습니다.

송출 순서와 실패 처리: ① 화면 캡처 → ② WHIP 연결(소유자 access token을 `Authorization: Bearer`로) → ③ `POST /api/broadcasts/{id}/start`. 상태 전이는 되돌릴 수 없어 마지막에 하고, ②·③ 중 하나라도 실패하면 WHIP·캡처를 정리해 방송은 `대기`로 남습니다. 이미 `방송 중`인 방송(송출 탭이 꺼졌던 경우)은 ③ 없이 송출만 다시 붙습니다.

**송출 인가:** MediaMTX가 WHIP 요청마다 백엔드 `/internal/media-auth`에 묻고(`authMethod: http`), 백엔드는 토큰의 사용자가 그 스트림 키 방송의 **소유자**이고 방송이 **끝나지 않았을 때만** 허용합니다. 스트림 키를 알아도 토큰 없음·타인 토큰·종료된 방송은 `401`. 시청(HLS)은 공개입니다.

### 모바일 시청
같은 와이파이의 휴대폰에서 `http://<이 PC의 LAN IP>:8000/watch.html` → 재생. (시청은 HTTPS가 필요 없음. Windows 방화벽이 8000·8888 포트를 막으면 허용 필요 — **아직 실기기 미확인**)

### 모바일 송출 (개발 중 — D8)
모바일 카메라 송출 페이지는 아직 없습니다. 휴대폰 브라우저는 `localhost`가 아니면 HTTPS에서만 카메라를 열 수 있어, 그때는 HTTPS 프로필을 씁니다:
```bash
# .env: DOMAIN=<PC LAN IP>, PUBLIC_HOST=<PC LAN IP>
docker compose --profile https up -d   # → https://<PC LAN IP>/  (사설 인증서 경고는 수락)
```

### 인증·인가 확인 (API)
화면 대신 API로도 확인할 수 있습니다 (Git Bash / macOS / Linux 기준):
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
| `GET /api/broadcasts[?status=LIVE]`, `GET /api/broadcasts/{id}` | 공개 | 방송 목록(최신순, 상태 필터)·상세. 응답에 방송자·스트림 키·`hlsPath`(시청 주소) |
| `POST /api/broadcasts` | 로그인 | `{title, description?}` 방송 등록 (등록자 = 소유자, 스트림 키 자동 발급) |
| `PATCH /api/broadcasts/{id}`, `DELETE /api/broadcasts/{id}` | **소유자** | `{title, description?}` 수정 · 삭제. 타인 403, 비로그인 401 |
| `POST /api/broadcasts/{id}/start`, `/stop` | **소유자** | 상태 `READY → LIVE → ENDED` 한 방향만. 순서가 틀리면 409. 송출 페이지가 WHIP 연결·끊기와 묶어 부릅니다 |
| `POST /internal/media-auth` | MediaMTX 전용 | MediaMTX `authHTTPAddress`. `action=publish`면 Bearer 토큰 사용자가 그 방송 소유자이고 종료 전일 때만 200, 아니면 401. 나머지(시청)는 200. nginx·Caddy는 이 경로를 넘기지 않습니다 |

인가는 전부 서버에서 합니다: 로그인 여부는 [SecurityConfig](./server/src/main/java/com/livestream/config/SecurityConfig.java), 소유자는 [BroadcastService.requireOwner](./server/src/main/java/com/livestream/broadcast/BroadcastService.java). 오류 응답은 `{"status":403,"detail":"..."}` 형식(RFC 9457).

### 백엔드 테스트
```bash
cd server
./gradlew test
```
- JDK 21 + **Docker 필요** (Testcontainers가 실제 PostgreSQL을 띄워 Flyway 스키마로 돌립니다).
- 검증 범위: 가입·로그인·내정보·재발급(회전)·로그아웃, 중복 아이디 409, 잘못된 입력 400, 틀린 비번/없는 아이디 401, 위조·만료 토큰 거부, 테스트 계정 로그인, 방송 인가 **비로그인 401 / 타인 403(데이터 불변) / 소유자 허용**, 송출 인가(MediaMTX → 백엔드) 소유자·대기/방송 중만 허용·타인/무토큰/위조토큰/없는 키/종료된 방송 401·시청 공개, 방송 등록(스트림 키·HLS 주소)·수정·상태 필터·상태 전이 409·입력 검증 400.
- ⚠️ Windows에서 프로젝트 경로에 **한글**이 있으면 Gradle 테스트 워커가 `ClassNotFoundException`으로 실패합니다. 영문 경로에 두거나 `./gradlew test -Dorg.gradle.jvmargs=-Dfile.encoding=MS949`로 실행하세요.

### 자동 E2E 테스트
```bash
npm install
npm run test:e2e
```
- `tests/broadcast-loop.spec.js`: 새 사용자로 방송을 만들고 **소유자 로그인 상태의 송출 페이지에서 방송 시작** → 설치된 Edge로 **진짜 탭을 캡처**해 WHIP 송출 → HLS 시청까지 돌립니다. 판정: 상태 `READY → LIVE`(시작 한 번에), 시청 쪽 소리 주파수 분석으로 **탭 소리와 마이크가 둘 다 섞여 오는지**, 영상 속 시계로 **영상·지연**, 캡처 탭을 닫으면 **송출 종료 + 상태 `ENDED`**, 토큰 없음·타인 토큰·종료된 방송의 WHIP는 **401**. (마이크는 1000Hz 발진기로 대신, 브라우저 공유 창 UI는 실행 옵션으로 건너뜀)
- 두 테스트 모두 `docker compose up -d` 상태(백엔드·송출 인가 포함)에서 돌고, 백엔드가 없으면 건너뜁니다.
- 테스트 중 스피커에서 삐 소리가 납니다.
- 다른 주소 대상: `E2E_BASE=https://<도메인> npm run test:e2e`
- `tests/broadcast-crud.spec.js`: **화면으로** 가입 → 방송 만들기 → 수정 → (상태 API로) 시작 → 목록 '방송 중' 탭 → 상세에서 종료 → 비로그인·`demo2` 화면에 관리 영역 없음 + API 직접 호출 401/403 → 소유자 삭제. `docker compose up -d` 상태에서만 돌고, 백엔드가 없으면 건너뜁니다.

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
