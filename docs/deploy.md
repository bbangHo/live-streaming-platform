# 배포 런북 — 공인 HTTPS 주소 (duckdns + Let's Encrypt)

목표: `https://<이름>.duckdns.org` 하나로 페이지·API·송출(WHIP)·시청(HLS)·녹화 재생을 모두 연다. 인증서는 Caddy가 Let's Encrypt에서 자동으로 받는다(`compose.yml`의 `https` 프로필).
사람이 할 일(외부 계정·서버)은 🖐로 표시했다.

## 1. 준비물
| 항목 | 내용 |
|---|---|
| 🖐 서버 | 공인 IP가 있는 Linux VM(클라우드 또는 포트포워딩한 미니PC). 2 vCPU·4GB RAM 이상 권장(백엔드 이미지 빌드에 메모리를 씀) |
| 🖐 duckdns | https://www.duckdns.org 로그인 → 서브도메인 생성 → **token** 확인 |
| 서버 소프트웨어 | Docker + Docker Compose v2, git |

## 2. 도메인을 서버 IP로
🖐 duckdns 화면에서 서브도메인의 IP를 서버 공인 IP로 맞춘다. IP가 바뀌는 환경이면 서버에서 5분마다 갱신한다:
```bash
# crontab -e  (DUCKDNS_TOKEN은 이 파일에만 — 레포에 넣지 않는다)
*/5 * * * * curl -s "https://www.duckdns.org/update?domains=<이름>&token=<DUCKDNS_TOKEN>&ip=" >/dev/null
```
확인: `nslookup <이름>.duckdns.org` → 서버 IP.

## 3. 방화벽 (클라우드 보안그룹 + 서버 방화벽 둘 다)
| 열기 | 용도 |
|---|---|
| `80/tcp` | Let's Encrypt 인증(HTTP-01) + https로 이동 |
| `443/tcp`, `443/udp` | 서비스 전체 (HTTP/3 포함) |
| `8189/udp`, `8189/tcp` | WebRTC 미디어 (송출 영상·소리). **이게 막히면 송출이 '연결 중'에서 멈춘다** |

**닫아 둘 것:** `8000`(nginx), `8080`(백엔드), `8888`·`8889`(MediaMTX). compose가 호스트에 열긴 하지만, 밖에서는 443(Caddy)으로만 들어오게 한다. 내부 인증 주소는 비밀값으로도 막혀 있다.

## 4. 코드와 `.env`
```bash
git clone https://github.com/bbangHo/live-streaming-platform.git && cd live-streaming-platform
cp .env.example .env
```
`.env`에서 **반드시** 바꿀 것:
```bash
DOMAIN=<이름>.duckdns.org
PUBLIC_HOST=<서버 공인 IP>            # WebRTC가 브라우저에 알려 줄 주소
STREAM_DEV_BYPASS=false              # 개발용 무인증 송출 끄기
POSTGRES_PASSWORD=<openssl rand -hex 24>
JWT_SECRET=<openssl rand -base64 48>
MEDIA_HOOK_SECRET=<openssl rand -hex 24>
# 선택: TOSS_CLIENT_KEY / TOSS_SECRET_KEY (토스 테스트 키, 없으면 내장 테스트 결제창)
```
`.env`는 커밋하지 않는다(`.gitignore`에 있음).

## 5. 기동
```bash
docker compose --profile https up -d --build
docker compose logs -f caddy      # "certificate obtained successfully" 확인 후 Ctrl+C
```
처음에는 백엔드 이미지 빌드(Gradle) 때문에 몇 분 걸린다. 인증서 발급에 실패하면 80 포트와 DNS를 먼저 확인한다. Let's Encrypt는 연속 실패하면 한동안 막으니, 고친 뒤에 재시도한다.

## 6. 확인 (제출 전 체크)
1. `https://<이름>.duckdns.org/`가 자물쇠 표시와 함께 열린다 → `demo1`/`demo1234`로 로그인
2. PC(Chrome·Edge): 방송 만들기 → **PC 화면 송출** → 「오디오 공유」를 켜고 시작 → 몇 초 안에 「방송 중」
3. 다른 기기·비로그인 브라우저: 목록 「방송 중」 → 재생, 소리 확인
4. 휴대폰(**와이파이 끄고 LTE**로 — NAT 확인): 로그인 → **모바일 카메라 송출** → 시작 → PC에서 시청
5. 방송 종료 → 「다시보기」에 녹화가 생기고 재생됨
6. `demo2`로 후원 → 합계 반영
7. 자동 점검(개발 PC에서, 백엔드가 필요한 스펙만):
   ```bash
   E2E_BASE=https://<이름>.duckdns.org npx playwright test tests/broadcast-crud.spec.js tests/broadcast-publish.spec.js
   ```
   (`broadcast-loop.spec.js`는 개발용 무인증 송출을 쓰므로 `STREAM_DEV_BYPASS=false`인 배포 환경에서는 실패하는 게 정상이다)

## 7. 운영 메모
- 데이터: `pgdata`(DB), `recordings`(녹화), `caddy_data`(인증서) 볼륨. `docker compose down`은 데이터를 지우지 않고, `down -v`는 **전부 지운다**.
- 업데이트: `git pull && docker compose --profile https up -d --build`
- 로그: `docker compose logs -f backend mediamtx`
- 송출이 「연결 실패」로 끝나면: 8189/udp 방화벽 → `PUBLIC_HOST`가 공인 IP인지 → 대칭형 NAT라면 TURN이 필요하다(README 알려진 한계)
