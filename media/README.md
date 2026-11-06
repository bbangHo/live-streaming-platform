# media — MediaMTX

브라우저 송출(WHIP)을 받아 HLS로 내보내는 미디어 서버. 설정은 [mediamtx.yml](mediamtx.yml) 하나. 여기 없는 키는 MediaMTX 기본값.

검증한 버전: **MediaMTX v1.21.3** (2026-11-06, Windows amd64)

## 실행

### 바이너리 (Windows)
1. [Releases](https://github.com/bluenviron/mediamtx/releases)에서 `mediamtx_v1.21.3_windows_amd64.zip`을 받아 `mediamtx.exe`만 이 폴더(`media/`)에 푼다. (`.gitignore`에 있어 커밋되지 않음)
2. 레포 루트에서:
   ```bash
   media/mediamtx.exe media/mediamtx.yml
   ```
   설정 파일을 고치면 MediaMTX가 자동으로 다시 읽는다.

### Docker
```bash
docker run --rm -it -v "$PWD/media/mediamtx.yml:/mediamtx.yml" -p 8888:8888 -p 8889:8889 -p 8189:8189/udp -p 9997:9997 bluenviron/mediamtx:1.21.3
```
Docker는 아직 실행해 보지 않았다. Docker Desktop(Windows/macOS)에선 WebRTC ICE 후보가 컨테이너 IP로 잡혀 연결이 안 될 수 있다 → `webrtcAdditionalHosts`에 호스트 IP를 넣는다. 그리고 설정의 `apiAddress: 127.0.0.1:9997`은 컨테이너 안에서만 열리므로 Docker에선 `:9997`로 바꿔야 호스트에서 API가 보인다.

## 포트

| 포트 | 용도 |
|---|---|
| 8889/tcp | WebRTC HTTP — WHIP 송출, WHEP 시청 |
| 8189/udp | WebRTC 미디어(ICE). 방화벽에서 열어야 함 |
| 8888/tcp | HLS (LL-HLS) |
| 9997/tcp | Control API (127.0.0.1만) |

RTSP·RTMP·SRT·MoQ는 껐다.

## 엔드포인트 (`<key>` = 스트림 키, `[A-Za-z0-9_-]+`)

| 용도 | URL |
|---|---|
| WHIP 송출 | `POST http://localhost:8889/live/<key>/whip` (body: SDP offer, `Content-Type: application/sdp`) → `201` + `Location`(세션 URL) |
| 송출 종료 | `DELETE <Location>` |
| HLS 시청 | `http://localhost:8888/live/<key>/index.m3u8` |
| WHEP 시청(저지연) | `POST http://localhost:8889/live/<key>/whep` |
| MediaMTX 내장 플레이어 | `http://localhost:8888/live/<key>/` (HLS), `http://localhost:8889/live/<key>/` (WebRTC) |
| 방송 중인 path 목록 | `GET http://127.0.0.1:9997/v3/paths/list` |

## 아직 안 한 것 (다음 단계)
- 스트림 키 인증: 아무나 아무 키로 송출 가능하다. D3(인증)에서 `authMethod: http` + `authHTTPAddress`로 백엔드가 검사하게 한다.
- CORS `*`: 배포 때 서비스 도메인으로 좁힌다.
- HTTPS: 배포(D2)에서 리버스 프록시로 감싼다.
