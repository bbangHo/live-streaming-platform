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
| [tests/broadcast-loop.spec.js](../tests/broadcast-loop.spec.js) | Playwright E2E: 진짜 탭 캡처로 송출→시청 루프 자동 판정 |

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

### 자동 E2E (Playwright)
```bash
npm install
npm run test:e2e
```
- 설치된 **Edge**를 쓴다(`channel: 'msedge'`, 브라우저 따로 안 받음). MediaMTX·페이지 서버는 켜져 있으면 그대로 쓰고, 없으면 Playwright가 띄운다(`media/mediamtx.exe` 필요).
- 테스트 중 **스피커에서 440Hz/1000Hz 삐 소리가 난다**(탭 소리를 실제로 재생해야 캡처되므로).

## 무엇이 검증됐나

### ✅ 서버측·자동으로 확인됨 (2026-11-06, Windows 11, MediaMTX v1.21.3)
- MediaMTX가 설정을 읽고 뜬다: `[HLS] listener :8888`, `[WebRTC] listeners :8889 (TCP), :8189 (UDP/ICE)`, `[API] 127.0.0.1:9997`.
- `curl`: WHIP 엔드포인트 CORS preflight `OPTIONS` → `204`, 잘못된 SDP `POST` → `400`(엔드포인트 살아 있음), 방송 전 HLS → `302`(쿠키 확인) → `404`, API path 목록 → 빈 배열.
- **Playwright E2E** (`tests/broadcast-loop.spec.js`, Edge 헤드리스, 5회 연속 통과):
  - **진짜 탭 캡처**: 실행 옵션 `--auto-select-tab-capture-source-by-title`로 공유 창만 건너뛰고, 실제 탭의 화면과 소리를 `getDisplayMedia`로 잡는다. 가짜 장치 아님.
  - **마이크는 흉내**: `getUserMedia`를 1000Hz 발진기로 바꿔 넣는다(테스트 PC에 마이크가 없어도 돌게). publish.html의 믹싱 코드는 그대로 탄다.
  - **두 소리가 섞여 시청 쪽까지 오는지**: watch.html 소리를 주파수 분석(2초 평균). 탭 440Hz 약 -38dB, 마이크 1000Hz 약 -18dB, 잡음(700Hz) 약 -83dB → 둘 다 잡음보다 40dB 이상 큼.
  - **이 검사가 진짜 잡는지**: 탭 소리를 끄면(`--mute-audio`) 440Hz가 -93dB로 떨어지고 테스트가 **실패**하는 것을 확인함.
  - **영상 내용·지연**: 탭에 현재 시각을 흑백 블록으로 그리고 시청 화면에서 읽는다. 읽은 값이 맞고 계속 흐름 → 영상이 제대로 오는 라이브. **화면→시청 지연 약 1~4초(측정값 1.05~3.95초)**(로컬, 800x400 탭).
  - **공유 중지 시 자동 종료**: 캡처 중인 탭을 닫으면 publish.html이 `종료`로 바뀌고 MediaMTX에서 path가 사라짐.
- 즉 **송출→서버→HLS 변환→hls.js 재생→종료** 루프와 **소리 믹싱**은 자동으로 끝까지 확인됐다.

### ⚠️ 브라우저 수동확인 필요 (사람이 클릭해야 함, 아직 안 함)
- **공유 창 UI 자체**: 탭/화면 선택 + "오디오 공유" 체크. 이건 페이지가 아니라 브라우저 화면이라 자동화가 안 된다(테스트는 실행 옵션으로 건너뜀).
- **진짜 마이크**: 테스트는 발진기로 대신했다. 실제 마이크 권한 허용·소리는 사람이 확인.
- **전체 화면 + 시스템 소리**(탭이 아닌 경우), 실제 1080p 화면에서 화질·지연.
- Safari(macOS/iOS) 시청. MediaMTX가 `part duration changed ... will cause an error in iOS clients` 경고를 계속 낸다. 오디오가 Opus라 구형 Safari에서 소리가 안 날 수도 있다 → 모바일 시청 검증(D9) 때 확인. Playwright의 WebKit은 진짜 Safari가 아니라 대신할 수 없다.
- 다른 기기(같은 와이파이 폰)에서 시청: localhost가 아니라 PC IP로 열어야 하고, 시청만이라 secure context는 필요 없지만 확인 안 함.

## 알게 된 것 / 주의
- **영상 코덱은 H264로 강제**해야 한다. Chrome 기본은 VP8인데 MediaMTX HLS(fMP4)는 VP8을 못 담는다 → `publish.html`에서 `setCodecPreferences`로 H264 우선.
- 오디오는 브라우저가 Opus로만 보낸다. HLS도 Opus(fMP4)로 나간다. Chrome/Edge/Firefox는 재생되지만 Safari 호환은 위 수동확인 대상.
- hls.js가 `bufferSeekOverHole`(치명적 아님)을 몇 번 낸다. 재생은 계속된다. LL-HLS 첫 구간 틈 메우기라 지금은 무시.
- trickle ICE 없이 후보를 다 모은 뒤 한 번에 POST한다(최대 3초 대기). 로컬은 1초 안에 연결됨.
- Playwright는 기본으로 `--mute-audio`를 붙여 **탭 소리 캡처가 무음**이 된다 → `ignoreDefaultArgs`로 뺐다.
- MediaMTX v1.21은 **MoQ**가 기본으로 켜져 있어 껐다(`moq: no`).
- 스트림 키 인증 없음 — 아무 키로나 송출 가능. D3에서 MediaMTX `authHTTPAddress` → 백엔드 검사로 막는다.
