# live-streaming-platform

치지직 같은 실시간 라이브 방송 송출·시청 웹 서비스. (Codemit 공모전 제출작)

> 상태: **개발 중** · 마감 2026-11-19 23:59 · 평가 기준·계획은 [PLAN.md](./PLAN.md) 참고
> 현재 구현: 회원가입·로그인(JWT)·소유자 인가, 방송 등록·목록·상세(시청)·수정·삭제, **PC 화면 송출**(화면 + 탭/시스템 소리 + 마이크)·**모바일 카메라 송출**(WHIP) → MediaMTX → HLS 시청, **송출 권한 서버 검증**(방송 소유자 토큰만 송출 가능), **방송 상태 자동 연동**(송출이 들어오면 방송 중, 끊긴 채 30초면 종료). **댓글**(작성자만 수정·삭제), **녹화·다시보기**(자동 녹화, 위치 DB 저장, 재생, 소유자 삭제), **도네이션**(테스트 결제, 서버 승인 검증, 중복 적립 방지, 합계).

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
| http://localhost:8000/recordings.html | 다시보기(녹화) 목록 |
| http://localhost:8000/publish-pc.html?id=… | PC 송출 (방송 상세 → 「PC 화면 송출」) |
| http://localhost:8000/publish-mobile.html?id=… | 모바일 카메라 송출 (방송 상세 → 「모바일 카메라 송출」, 휴대폰은 HTTPS 필요) |
| http://localhost:8000/dev-publish.html, dev-watch.html | 개발용: 스트림 키를 직접 넣는 송출·시청 (등록 안 된 키만, `STREAM_DEV_BYPASS=true`일 때) |
| `:8889` / `:8888` / `:8189` (UDP·TCP) | MediaMTX — WHIP / HLS / WebRTC 미디어 (녹화 재생 `:9996`은 내부망 전용, 페이지 주소 `/playback/`으로 접근) |
| http://localhost:8080/api | 백엔드 API (Spring Boot) — 인증/인가, 방송 정보 |
| (내부) postgres | DB. `pgdata` 볼륨에 저장 → 재시작·`down` 후에도 유지 (`down -v`만 삭제) |
| (내부) 녹화 파일 | MediaMTX `recordings` 볼륨 `/recordings/live/<스트림 키>/<시작 시각>.mp4` (`down -v`만 삭제) |

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
| `MEDIA_HOOK_SECRET` | 로컬 기본값 | MediaMTX → 백엔드 송출 인증 요청에 붙는 공유 비밀값. **배포 시 반드시 .env로 지정** |
| `STREAM_DEV_BYPASS` | `true` | 등록 안 된 스트림 키(개발용 페이지)로도 로그인 없이 송출 허용. **배포 시 `false`** |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | (비어 있음) | 도네이션용 [토스페이먼츠](https://developers.tosspayments.com) **테스트** 키. 비우면 내장 테스트 결제창 |

### Docker 없이 실행
MediaMTX 바이너리를 받아 직접 띄웁니다 → [media/README.md](./media/README.md)
```bash
media/mediamtx.exe media/mediamtx.yml
python -m http.server 8000 --bind 127.0.0.1 -d web
```
이 모드는 백엔드가 없어 개발용 페이지(`dev-publish.html`, `dev-watch.html`)만 동작합니다.

## 기능 확인

### 방송 만들기 → PC 송출 → 시청 → 종료
1. http://localhost:8000/ → 우측 상단 **로그인** → 테스트 계정 `demo1` / `demo1234` (또는 회원가입).
2. **방송 만들기**에 제목·설명 입력 → 상세 페이지로 이동. 소유자에게만 **방송 관리**(PC/모바일 송출, 종료, 삭제, 수정)가 보입니다.
3. 소리가 나는 탭을 하나 띄워 둡니다 (예: 유튜브).
4. **PC 화면 송출** → **방송 시작** → 공유 창에서 그 탭 선택 + **「오디오 공유」 켜기** → 마이크 허용.
   로그에 `화면 소리 1개, 마이크 1개`, 연결 상태 `송출 중`이 뜨고, 2초 안에 방송 상태가 **방송 중**으로 바뀝니다(서버가 MediaMTX를 보고 바꿈 — 시작 버튼 API 없음).
5. **시청:** 다른 브라우저(또는 시크릿 창, 비로그인)로 http://localhost:8000/ → **방송 중** 탭 → 방송 클릭 → 영상 컨트롤에서 음소거 해제. 1~4초 지연으로 화면과 **탭 소리 + 마이크 소리**가 나옵니다.
6. **종료:** 송출 페이지 또는 상세 페이지의 **방송 종료** → 상태 **종료**, 송출이 끊기고 시청 화면은 「종료된 방송입니다」. 종료한 방송에는 다시 송출할 수 없습니다(서버가 거부).
7. **끊김 처리:** 송출 중에 송출 페이지를 닫거나 「공유 중지」를 누르면 방송은 **30초 동안 방송 중을 유지**하고, 그 안에 「방송 시작」을 다시 누르면 이어집니다. 30초가 지나면 자동으로 **종료**됩니다.
8. **인가:** 로그아웃하거나 `demo2`로 로그인해 같은 방송을 열면 관리 영역이 없고, 송출 페이지도 「소유자만 송출할 수 있습니다」. API·WHIP로 직접 시도해도 401/403입니다(아래).
9. `docker compose restart` 또는 `down` → `up` 후에도 방송이 그대로 조회됩니다(PostgreSQL 볼륨).

### 댓글 (선택 기능)
방송 상세 아래 **댓글**: 비로그인도 읽을 수 있고, 로그인하면 쓸 수 있습니다. 내 댓글에만 **수정·삭제**가 보이며(수정하면 「(수정됨)」), 남의 댓글은 방송 소유자라도 서버가 403으로 막습니다. 다른 시청자 화면에는 3초 안에 반영됩니다.

### 녹화·다시보기 (선택 기능)
1. 방송은 송출되는 동안 **자동으로 녹화**됩니다(MediaMTX, fMP4). 송출이 끝나면(또는 끊겼다 다시 붙거나 1시간마다) 녹화 파일이 하나 완성되고, MediaMTX가 백엔드에 알려 **파일 위치·시작 시각·길이가 DB에 저장**됩니다.
2. 방송을 끝내고 몇 초 뒤 방송 상세의 **다시보기**, 또는 상단 메뉴 **다시보기**(전체 녹화 목록)에 나타납니다. 비로그인도 볼 수 있습니다.
3. 녹화 상세에서 바로 재생·탐색되고, **저장 위치**(파일 경로)가 보입니다. `docker compose down` → `up` 후에도 목록·재생이 유지됩니다(DB·녹화 볼륨).
4. **녹화 삭제**는 방송 소유자에게만 보이고(파일도 삭제), 타인은 API로 시도해도 403입니다.
- 개발용 페이지(`dev-publish.html`)의 미등록 키 송출도 파일은 녹화되지만 DB에는 저장하지 않습니다(목록에 안 나옴).

### 도네이션 (선택 기능)
1. `demo2`로 로그인 → `demo1`의 방송 상세 → **후원**에서 금액(1,000원~)·메시지 → **후원하기**. (내 방송에는 후원 버튼이 없습니다)
2. **테스트 결제창**: 토스 키가 없으면 내장 테스트 결제창이 뜹니다 → 카드 `4242 4242 4242 4242` → **결제하기**. (`TOSS_CLIENT_KEY`·`TOSS_SECRET_KEY`에 토스페이먼츠 **테스트** 키를 넣으면 토스 결제창으로 바뀝니다. 어느 쪽도 실제 돈은 나가지 않습니다)
3. 결과 페이지 「5,000원 후원 완료」 → 방송 화면의 **후원 합계·최근 후원**에 반영(다른 시청자 화면에도 5초 안에).
4. **중복 적립 방지 확인:** 결과 페이지를 **새로고침**(같은 승인 요청 반복)해도 「후원 완료」만 다시 뜨고 합계는 그대로입니다.
5. **서버 검증 확인:** 결제창 주소의 `amount`를 바꿔 결제하면 「주문 금액과 다름」으로 거부, 거절 카드 `4000 0000 0000 0002`면 「승인되지 않았습니다」 — 둘 다 합계에 안 더해집니다.

흐름: ① 주문 생성(서버가 금액 기억, 상태 `PENDING`) → ② 결제창(PG가 `paymentKey` 발급) → ③ `POST /api/donations/confirm` — 서버가 주문 금액 대조·주문자 확인 → PG 승인 API 호출(토스는 `Idempotency-Key`=주문 번호) → 승인 금액까지 맞으면 그 주문 **한 행만** `DONE`. 주문 행을 잠그고(`SELECT … FOR UPDATE`) `PENDING`일 때만 바꾸므로 동시에 여러 번 와도 한 번만 적립되고, 합계는 `DONE` 행의 합으로 계산합니다(따로 올리는 카운터 없음) → [DonationService](./server/src/main/java/com/livestream/donation/DonationService.java)

### 모바일 송출 (카메라 + 마이크)
휴대폰 브라우저는 `localhost`가 아니면 **HTTPS에서만** 카메라를 엽니다. 같은 와이파이의 PC에서 HTTPS 프로필로 띄웁니다:
```bash
# .env: DOMAIN=<PC LAN IP>, PUBLIC_HOST=<PC LAN IP>
docker compose --profile https up -d   # → https://<PC LAN IP>/  (사설 인증서 경고는 수락)
```
휴대폰에서 로그인 → 방송 상세 → **모바일 카메라 송출** → **방송 시작** → 카메라·마이크 허용. 방송 중에도 **카메라 전환**(전면/후면)이 끊김 없이 됩니다. PC 브라우저에서도 웹캠으로 같은 페이지를 써 볼 수 있습니다. (**실기기 확인은 D9 예정**)

### 모바일 시청
같은 와이파이의 휴대폰에서 `http://<이 PC의 LAN IP>:8000/` → 방송 클릭. (시청은 HTTPS가 필요 없음. Windows 방화벽이 8000 포트를 막으면 허용 필요 — **아직 실기기 미확인**)

### 송출 권한이 서버에서 검증되는 방식
스트림 키는 시청 주소(`/live/<키>/index.m3u8`)에 쓰이는 **공개 값**이라, 키만으로는 송출할 수 없게 했습니다.
1. 송출 페이지가 WHIP 요청에 로그인 토큰을 붙입니다: `POST /live/<키>/whip` + `Authorization: Bearer <access token>`.
2. MediaMTX가 송출(publish)마다 백엔드에 묻습니다(`authMethod: http`) → [StreamAuthService](./server/src/main/java/com/livestream/stream/StreamAuthService.java): **토큰이 유효 + 그 방송의 소유자 + 종료되지 않은 방송**일 때만 허용, 아니면 401. 시청(read)은 공개.
3. 백엔드가 2초마다 MediaMTX의 송출 목록을 보고 상태를 맞춥니다 → [StreamStatusSync](./server/src/main/java/com/livestream/stream/StreamStatusSync.java): 송출이 들어오면 방송 중, 끊긴 채 30초면 종료, 종료된 방송에 남은 송출은 끊음(kick). 같은 방송에 두 번째 송출은 MediaMTX가 거부(`overridePublisher: no`).
4. 이 내부 인증 주소(`/api/internal/...`)는 nginx·Caddy가 밖에서 막고, 공유 비밀값(`MEDIA_HOOK_SECRET`)이 없으면 백엔드가 거부합니다.

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
# 4) demo1 방송의 스트림 키로 WHIP 송출 시도: 토큰 없음·demo2 토큰 → 401
KEY=$(curl -s $B/broadcasts/1 | sed 's/.*"streamKey":"\([^"]*\)".*/\1/')
curl -i -X POST http://localhost:8000/live/$KEY/whip -H 'Content-Type: application/sdp' -H "Authorization: Bearer <demo2 accessToken>" -d 'v=0'
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
| `POST /api/broadcasts/{id}/stop` | **소유자** | 방송 종료 `LIVE → ENDED` (송출 중이면 끊김). 방송 중이 아니면 409. 시작 API는 없음 — 송출이 MediaMTX에 들어오면 서버가 `READY → LIVE` |
| `GET /api/broadcasts/{id}/comments[?after=<댓글 id>]` | 공개 | 댓글 목록(오래된 순, 최대 200개). `after`면 그 뒤 것만 |
| `POST /api/broadcasts/{id}/comments` | 로그인 | `{content}` 댓글 작성 (1~500자) |
| `PATCH /api/comments/{id}`, `DELETE /api/comments/{id}` | **작성자** | 댓글 수정·삭제. 타인(방송 소유자 포함) 403, 비로그인 401 |
| `GET /api/recordings`, `GET /api/recordings/{id}`, `GET /api/broadcasts/{id}/recordings` | 공개 | 녹화 목록(최신순)·상세·방송별 목록. `filePath`(저장 위치), `startedAt`, `durationMs`, `playbackPath`(재생 주소) |
| `DELETE /api/recordings/{id}` | **방송 소유자** | 녹화 삭제(파일 + DB). 타인 403 |
| `GET /api/broadcasts/{id}/donations` | 공개 | 후원 합계 `{total, count, recent[]}` (승인된 것만) |
| `POST /api/broadcasts/{id}/donations` | 로그인 | `{amount, message?}` 주문 생성 → `{orderId, amount, orderName, provider, clientKey}`. 금액 1,000~1,000,000원, 본인 방송 400 |
| `POST /api/donations/confirm` | 로그인(**주문자**) | `{paymentKey, orderId, amount}` 승인 → 200 `DONE` / 402 `FAILED`(PG 거절). 금액 불일치 400, 남의 주문 403, 다른 결제로 이미 승인·이미 쓴 결제 키 409. 같은 요청 반복은 같은 결과(재적립 없음) |
| `GET /api/payments/config`, `POST /api/test-pg/pay` | 공개 | 결제창 종류(`toss`/`test`), 내장 테스트 PG 결제창의 카드 결제(적립은 confirm에서만) |
| `POST /live/{streamKey}/whip` | **소유자** (Bearer) | WHIP 송출(MediaMTX). 토큰 없음·타인·종료된 방송 401, 같은 방송 두 번째 송출 거부 |

인가는 전부 서버에서 합니다: 로그인 여부는 [SecurityConfig](./server/src/main/java/com/livestream/config/SecurityConfig.java), 소유자는 [BroadcastService.requireOwner](./server/src/main/java/com/livestream/broadcast/BroadcastService.java). 오류 응답은 `{"status":403,"detail":"..."}` 형식(RFC 9457).

### 백엔드 테스트
```bash
cd server
./gradlew test
```
- JDK 21 + **Docker 필요** (Testcontainers가 실제 PostgreSQL을 띄워 Flyway 스키마로 돌립니다).
- 검증 범위: 가입·로그인·내정보·재발급(회전)·로그아웃, 중복 아이디 409, 잘못된 입력 400, 틀린 비번/없는 아이디 401, 위조·만료 토큰 거부, 테스트 계정 로그인, 방송 인가 **비로그인 401 / 타인 403(데이터 불변) / 소유자 허용**, 방송 등록(스트림 키·HLS 주소)·수정·상태 필터·상태 전이 409·입력 검증 400, **송출 인증**(소유자 토큰만 허용, 토큰 없음·위조·타인·종료된 방송·등록 안 된 키 거부, hook secret 불일치 403), **상태 동기화**(송출 들어오면 방송 중, 끊김 30초 유예·재접속 시 유지, 초과 시 종료, 종료된 방송의 송출 끊기), **댓글**(공개 조회·새 댓글만 조회, 로그인 작성, 작성자만 수정·삭제 — 타인·방송 소유자 403, 입력 검증, 방송 삭제 시 함께 삭제), **녹화**(완료 알림 저장·중복 알림 1회만 저장·파일 이름에서 시작 시각, 미등록 키 무시, 비밀값 검증, 공개 목록·재생 주소, 삭제는 방송 소유자만), **도네이션**(승인 시 합계 반영, 같은 승인 반복·**동시 승인 8건 → 1회 적립**, 다른 결제 키·이미 쓴 결제 키 409, 금액 조작 400, 카드 거절 402·적립 없음, 남의 주문 403, 금액 범위·본인 방송, 토스 승인 API 호출 형식(Basic 인증·Idempotency-Key)과 성공·거절·서버 오류 처리).
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
- `tests/broadcast-crud.spec.js`: **화면으로** 가입 → 방송 만들기 → 수정 → 송출 링크 → 비로그인·`demo2` 화면에 관리 영역 없음 + API 직접 호출 401/403 → 소유자 삭제. 댓글 작성 → 비로그인 조회 → 방송 소유자도 남의 댓글 403 → 작성자 수정·삭제가 다른 화면에 반영. 후원 테스트 결제 → 완료 → 결과 새로고침해도 합계 그대로 → 거절 카드·결제창 금액 조작은 실패하고 합계 그대로 → 소유자에게는 후원 버튼 없음.
- `tests/broadcast-publish.spec.js`: **PC 송출 페이지**에서 진짜 탭 캡처 송출 → 서버가 자동으로 방송 중 → 비로그인 시청자가 목록 '방송 중' 탭 → 상세에서 재생 → 토큰 없음·타인 토큰·두 번째 송출 거부 → 소유자 종료 → HLS 사라짐·시청 화면 종료 → 종료된 방송 재송출 거부. **모바일 송출 페이지**(가짜 카메라)로 송출 → 방송 중 → 종료 → 녹화가 상세 '다시보기'에 생김 → 비로그인이 다시보기 목록에서 재생 → 타인 삭제 403 → 소유자 삭제 시 파일까지 사라짐. 비로그인·타인의 송출 페이지 접근 차단.
- 위 두 파일은 백엔드가 필요해 `docker compose up -d` 상태에서만 돌고, 없으면 건너뜁니다. `broadcast-loop.spec.js`는 개발용 페이지(`dev-publish.html`)로 소리 믹싱·지연을 숫자로 판정합니다.

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

- [MediaMTX](https://github.com/bluenviron/mediamtx) (MIT) — 미디어 서버(송출·HLS·녹화·재생), Docker 이미지(`-ffmpeg` 변형: Alpine + FFmpeg(LGPL/GPL), 녹화 훅용 sh·wget)·바이너리로 사용 (레포에 포함하지 않음)
- [hls.js](https://github.com/video-dev/hls.js) (Apache-2.0) — CDN으로 로드
- [Caddy](https://caddyserver.com) (Apache-2.0), [nginx](https://nginx.org) (BSD-2) — Docker 이미지
- [Playwright](https://playwright.dev) (Apache-2.0) — 개발 의존성
- [Spring Boot](https://spring.io/projects/spring-boot) · Spring Security (Apache-2.0), [Hibernate ORM](https://hibernate.org) (Apache-2.0), [Flyway](https://github.com/flyway/flyway) (Apache-2.0) — 백엔드
- [JJWT](https://github.com/jwtk/jjwt) (Apache-2.0) — JWT 발급·검증
- [PostgreSQL](https://www.postgresql.org) (PostgreSQL License) — Docker 이미지, JDBC 드라이버(BSD-2)
- [Testcontainers](https://testcontainers.com) (MIT) — 백엔드 테스트
- [토스페이먼츠 JavaScript SDK v2](https://docs.tosspayments.com) — 토스 키를 넣었을 때만 CDN으로 로드 (결제 승인 API는 서버에서 직접 호출)
