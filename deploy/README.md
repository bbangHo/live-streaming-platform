# deploy — HTTPS 배포 (Docker Compose + Caddy)

`getUserMedia`/`getDisplayMedia`는 HTTPS에서만 동작하므로 배포는 HTTPS가 필수다. 이 폴더는 서버 한 대에 아래를 올린다.

```
브라우저 ──HTTPS(443)──▶ Caddy ─┬─ /live/<key>/whip|whep ─▶ mediamtx:8889 (WebRTC 시그널링)
                               ├─ /live/<key>/...       ─▶ mediamtx:8888 (HLS)
                               └─ 그 밖                  ─▶ web/ 정적 페이지
브라우저 ──UDP/TCP 8189────────────▶ mediamtx (WebRTC 미디어, Caddy를 거치지 않음)
```

- **한 도메인**이라 페이지가 같은 출처로 WHIP·HLS를 부른다. CORS는 `https://<DOMAIN>`만 허용한다.
- Caddy가 Let's Encrypt 인증서를 자동으로 받고 갱신한다.

## 로컬에서 HTTPS 확인 (확인함, 2026-11-06)
```bash
cp deploy/.env.example deploy/.env      # DOMAIN=localhost, PUBLIC_HOST=127.0.0.1
docker compose -f deploy/docker-compose.yml up -d
E2E_BASE=https://localhost npm run test:e2e
docker compose -f deploy/docker-compose.yml down
```
`https://localhost`는 Caddy 내부 인증서라 브라우저가 경고를 띄운다(테스트는 무시하도록 설정됨).

## 서버에 배포 (아직 안 함)
1. 공인 IP가 있는 Linux 서버 + Docker.
2. 도메인 DNS `A` 레코드 → 서버 IP. Cloudflare를 쓰면 **프록시 끔(DNS only)**: 8189/UDP는 Cloudflare를 못 지난다.
3. 방화벽 열기: `80/tcp`, `443/tcp`, `443/udp`, `8189/udp`, `8189/tcp`.
4. 레포를 받고 `deploy/.env` 작성: `DOMAIN=<도메인>`, `PUBLIC_HOST=<서버 공인 IP>`.
5. `docker compose -f deploy/docker-compose.yml up -d`
6. 확인: `E2E_BASE=https://<도메인> npm run test:e2e` (내 PC에서 실행 → 실제 배포 서버로 송출·시청 한 바퀴)

## 주의
- 스트림 키 인증이 아직 없다 → 배포하면 누구나 송출 가능. D3(인증) 전에는 오래 열어 두지 말 것.
- `caddy_data` 볼륨을 지우면 인증서를 다시 받는다. Let's Encrypt는 같은 도메인 발급 횟수 제한이 있다.
- 대칭 NAT 뒤 시청자·송출자는 UDP·TCP 직접 연결이 모두 안 될 수 있다 → 그때 TURN(coturn) 추가.
