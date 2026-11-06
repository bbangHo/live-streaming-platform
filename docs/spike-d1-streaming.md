# D1 스파이크 — 브라우저 WHIP 송출 → MediaMTX → HLS 시청

> 2026-11-06 · PLAN.md 3장 1주차 D1. 목표: 가장 불확실한 영상 전송 구간이 실제로 도는지 최소 코드로 확인.

```
publish.html ── getDisplayMedia(화면+탭/시스템 소리) + getUserMedia(마이크)
     │          Web Audio로 소리 2개 → 오디오 트랙 1개로 믹싱
     │  WHIP: POST SDP (H264 + Opus)
     ▼
MediaMTX :8889 ──(path live/<key>)──▶ LL-HLS :8888 ──▶ watch.html (hls.js)
```

## 파일
| 파일 | 내용 |
|---|---|
| [media/mediamtx.yml](../media/mediamtx.yml) | WHIP 수신 + LL-HLS 출력, path `live/<key>` |
| [media/README.md](../media/README.md) | 실행법·포트·엔드포인트 |
| [web/publish.html](../web/publish.html) | 화면+소리+마이크 믹싱 → WHIP 송출, 시작/종료 |
| [web/watch.html](../web/watch.html) | hls.js로 HLS 재생 (Safari는 네이티브 HLS) |
| [scripts/spike-d1-e2e.mjs](../scripts/spike-d1-e2e.mjs) | 헤드리스 Edge로 송출→시청 루프 자동 확인 |

## 로컬 테스트 절차 (localhost)

`getDisplayMedia`/`getUserMedia`는 secure context(HTTPS 또는 `localhost`)에서만 동작한다. 아래는 모두 `localhost` 기준. **`127.0.0.1`이 아닌 `localhost`로 열 것**(둘 다 secure context지만 안내를 하나로 통일).

1. MediaMTX 실행 (내려받기는 [media/README.md](../media/README.md)):
   ```bash
   media/mediamtx.exe media/mediamtx.yml
   ```
2. 정적 페이지 서버 (레포 루트에서, 다른 터미널):
   ```bash
   python -m http.server 8000 --bind 127.0.0.1 -d web
   ```
3. 송출: Chrome/Edge로 `http://localhost:8000/publish.html` → 스트림 키 `test` → **방송 시작** → 공유 창에서 **탭 또는 전체 화면 + "오디오 공유" 체크** → 마이크 권한 허용. 로그에 `WHIP 201`, 상태 `connected`가 떠야 한다.
4. 시청: 다른 탭/창(또는 다른 브라우저)으로 `http://localhost:8000/watch.html` → **재생**. 1~3초 지연으로 화면이 나와야 한다. 영상은 처음에 음소거(`muted`)로 자동재생되므로 컨트롤에서 소리를 켠다.
5. 종료: publish 페이지에서 **방송 종료**(또는 브라우저의 "공유 중지"). watch 쪽은 재생이 멈추고 3초마다 다시 시도한다.

자동 확인 (1·2가 켜진 상태에서): `node scripts/spike-d1-e2e.mjs`

## 무엇이 검증됐나

### ✅ 서버측·자동으로 확인됨 (2026-11-06, Windows 11, MediaMTX v1.21.3)
- MediaMTX가 설정을 읽고 뜬다: `[HLS] listener :8888`, `[WebRTC] listeners :8889 (TCP), :8189 (UDP/ICE)`, `[API] 127.0.0.1:9997`.
- `curl`: WHIP 엔드포인트 CORS preflight `OPTIONS` → `204`, 잘못된 SDP `POST` → `400`(엔드포인트 살아 있음), 방송 전 HLS → `302`(쿠키 확인) → `404`, API path 목록 → 빈 배열.
- **헤드리스 Edge + 가짜 화면·마이크**(`--use-fake-device-for-media-stream --use-fake-ui-for-media-stream`)로 `publish.html`·`watch.html`을 그대로 구동한 결과 5개 모두 PASS:
  1. publish.html에서 화면 소리 1 + 마이크 1 트랙 획득·믹싱 → WHIP `201` + `Location` 세션 URL 수신 → `RTCPeerConnection connected`
  2. MediaMTX 로그 `stream is available and online, 2 tracks (Opus, H264)`, API에서 path `live/e2e` ready
  3. HLS master playlist 응답 (`CODECS="avc1.42041f,opus"`, 800x450)
  4. watch.html에서 hls.js가 재생, `video.currentTime`이 계속 증가 (21.05s → 22.89s), 해상도 800x450
  5. 방송 종료 → WHIP `DELETE` → MediaMTX에서 path 사라짐
- 즉 **송출→서버→HLS 변환→hls.js 재생→종료** 루프는 코드 레벨에서 끝까지 돈다.

### ⚠️ 브라우저 수동확인 필요 (사람이 클릭해야 함, 아직 안 함)
- **실제** 화면 공유 권한 창에서 탭/화면 선택 + "오디오 공유" 체크가 되는지 (자동 테스트는 가짜 장치라 권한 창이 없다).
- **실제 탭/시스템 소리와 마이크가 둘 다 시청 쪽에서 들리는지** — 믹싱 결과는 귀로만 확인 가능.
- 실제 화면 해상도(1080p 등)에서 화질·지연(체감 몇 초인지).
- 브라우저 "공유 중지" 버튼으로 끊었을 때 자동 종료되는지.
- Safari(macOS/iOS) 시청. MediaMTX가 `part duration changed ... will cause an error in iOS clients` 경고를 계속 낸다(가짜 장치 기준). 오디오가 Opus라 구형 Safari에서 소리가 안 날 수도 있다 → 모바일 시청 검증(D9) 때 확인.
- 다른 기기(같은 와이파이 폰)에서 시청: localhost가 아니라 PC IP로 열어야 하고, 시청만이라 secure context는 필요 없지만 확인 안 함.

## 알게 된 것 / 주의
- **영상 코덱은 H264로 강제**해야 한다. Chrome 기본은 VP8인데 MediaMTX HLS(fMP4)는 VP8을 못 담는다 → `publish.html`에서 `setCodecPreferences`로 H264 우선.
- 오디오는 브라우저가 Opus로만 보낸다. HLS도 Opus(fMP4)로 나간다. Chrome/Edge/Firefox는 재생되지만 Safari 호환은 위 수동확인 대상.
- hls.js가 `bufferSeekOverHole`(치명적 아님)을 몇 번 낸다. 재생은 계속된다. LL-HLS 첫 구간 틈 메우기라 지금은 무시.
- trickle ICE 없이 후보를 다 모은 뒤 한 번에 POST한다(최대 3초 대기). 로컬은 1초 안에 연결됨.
- MediaMTX v1.21은 **MoQ**가 기본으로 켜져 있어 껐다(`moq: no`).
- 스트림 키 인증 없음 — 아무 키로나 송출 가능. D3에서 MediaMTX `authHTTPAddress` → 백엔드 검사로 막는다.
