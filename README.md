# live-streaming-platform

치지직 같은 실시간 라이브 방송 송출·시청 웹 서비스. (Codemit 공모전 제출작)

> 상태: **개발 중** · 마감 2026-11-19 23:59 · 평가 기준·계획은 [PLAN.md](./PLAN.md) 참고
> 현재 구현: 브라우저 화면 송출(WHIP) → MediaMTX → HLS 시청. 로그인·방송 관리·모바일 송출은 개발 중.

---

## 심사자용 안내

- **서비스 주소:** 추후(제출 임박) 결정. 지금은 아래 **로컬 실행**으로 확인
- **테스트 계정:** 인증 기능 개발 후 기재 — TODO
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

종료: `docker compose down`

> ⚠️ **페이지를 `file://`로(파일 더블클릭) 열지 마세요.** Origin이 `null`이 되어 MediaMTX가 CORS 허용 헤더를 주지 않고, 송출이 `Failed to fetch`로 실패합니다. 반드시 위의 `http://localhost:8000/...` 주소로 엽니다. (`localhost`는 HTTPS가 아니어도 화면·마이크 캡처가 허용되는 secure context입니다.)

### 2. 환경변수 (선택)
없어도 PC 한 대에서는 그대로 동작합니다. 바꿀 때만 `cp .env.example .env` 후 수정 → [.env.example](./.env.example)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `WEB_PORT` | `8000` | 페이지 포트 |
| `PUBLIC_HOST` | `127.0.0.1` | WebRTC가 알려 줄 주소. 다른 기기에서 송출하면 이 PC의 LAN IP |
| `DOMAIN` | `localhost` | `--profile https`에서 인증서 낼 이름 |

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
- 로컬 실행: Docker Compose (MediaMTX + nginx, 선택 Caddy HTTPS)
- 테스트: Playwright (E2E)
- 백엔드 / 프론트 / DB: [PLAN.md](./PLAN.md)의 "기술 결정" 참고
- 문서: [docs/streaming-tech-reference.md](./docs/streaming-tech-reference.md)

## 출처·라이선스

- [MediaMTX](https://github.com/bluenviron/mediamtx) (MIT) — 미디어 서버, Docker 이미지·바이너리로 사용 (레포에 포함하지 않음)
- [hls.js](https://github.com/video-dev/hls.js) (Apache-2.0) — CDN으로 로드
- [Caddy](https://caddyserver.com) (Apache-2.0), [nginx](https://nginx.org) (BSD-2) — Docker 이미지
- [Playwright](https://playwright.dev) (Apache-2.0) — 개발 의존성
