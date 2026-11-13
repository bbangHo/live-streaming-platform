# 구현 계획 — 체크리스트 매핑 + 일정

> Codemit 실시간 방송 플랫폼 공모전 · 마감 2026-11-19 · 제출 1회
> 이 문서는 루브릭 체크리스트를 **"어디서 충족할지(코드 위치)"**로 매핑한다 → 제출 시 "요구사항 근거"로 그대로 복사.

---

## 1. 기술 결정 (lock)

| 레이어 | 선택 | 고정 여부 |
|---|---|---|
| **미디어 서버** | **MediaMTX** (WHIP 수신 → HLS + WHEP 출력 + 녹화) | **고정** (아래 제약 때문) |
| **송출(브라우저)** | WebRTC **WHIP** — PC `getDisplayMedia`+`getUserMedia`, 모바일 `getUserMedia` | **고정** |
| **시청** | 대중=**HLS**(hls.js), 저지연 옵션=WHEP | 고정 |
| 백엔드 | **Spring Boot 4 (Java 21)** + Spring Security · BCrypt · JWT(access+refresh, stateless) · JPA · Flyway → `server/` | **고정** (D3 확정) |
| 프론트 | 정적 HTML + 바닐라 JS + hls.js (빌드 없음, nginx·Caddy가 서빙) → `web/` | **고정** (D5 확정) |
| DB | **PostgreSQL** (영구 저장 필수) | 거의 고정 |
| 배포 | **HTTPS 필수** (getUserMedia는 secure context 전용) | **고정** |

> **왜 미디어 레이어가 고정인가:** 브라우저는 RTMP를 못 쏜다 → 웹 송출은 WebRTC(WHIP)가 유일. 시청 확장은 HLS. 이 둘을 한 통으로 잇는 게 MediaMTX. 백엔드 언어만 자유.

---

## 2. 체크리스트 ↔ 구현 매핑

> 각 항목의 "구현 위치"는 계획이며, 구현하면서 실제 파일·엔드포인트로 갱신한다. ✅표시는 제출 시 '충족' 체크 대상.

### 필수 — PC 라이브 방송 (D6 구현 완료)
| 체크리스트 | 구현 방법 | 위치 |
|---|---|---|
| 화면 캡처 실시간 송출 | `getDisplayMedia()` → WHIP(`Authorization: Bearer`)로 MediaMTX 송출 | [publish-pc.html](web/publish-pc.html) `capturePc` + [whip.js](web/whip.js) `publishWhip` |
| 탭/시스템 소리 + 마이크 함께 | `getDisplayMedia({audio:true})` + `getUserMedia(audio)` → Web Audio `MediaStreamDestination`으로 믹싱해 오디오 1트랙 | [publish-pc.html](web/publish-pc.html) `capturePc` — E2E가 시청 쪽 스펙트럼으로 두 소리 확인 |
| 마이크 음성 송출 | 위 믹싱 트랙 포함 (마이크 거부 시 화면 소리만) | 동上 |
| 시작/종료 시 연결 생성·종료 | 시작 = WHIP POST(연결 생성), 종료 = WHIP DELETE + `POST /api/broadcasts/{id}/stop` | [studio.js](web/studio.js) `start`·`end`·`stopSending` |

### 필수 — 모바일 라이브 방송 (D6 구현 완료, 실기기 확인은 D9)
| 체크리스트 | 구현 방법 | 위치 |
|---|---|---|
| 카메라+마이크 실시간 송출 | `getUserMedia({video:{facingMode}, audio})` → 같은 WHIP 경로 재사용. 방송 중 전면/후면 전환은 `replaceTrack`(재연결 없음) | [publish-mobile.html](web/publish-mobile.html) `captureCamera`·`flipCamera` |
| 마이크 음성 함께 | 같은 스트림의 audio 트랙 | 동上 |
| 시작·종료 | PC와 같은 흐름 | 공통 [studio.js](web/studio.js), [whip.js](web/whip.js) |

### 필수 — 방송 관리 및 시청
| 체크리스트 | 구현 방법 | (계획) 위치 |
|---|---|---|
| 등록·조회·수정·삭제 | REST CRUD + 화면 (D5 구현) | `GET·POST /api/broadcasts`, `GET·PATCH·DELETE /api/broadcasts/{id}` → [BroadcastController](server/src/main/java/com/livestream/broadcast/BroadcastController.java) · 화면 [index.html](web/index.html)(목록·만들기), [broadcast.html](web/broadcast.html)(상세·수정·삭제) |
| 목록·상세 + 다른 기기 수신·재생 | 목록(전체/방송 중 탭)·상세 페이지 + hls.js 플레이어. HLS는 같은 출처 `/live/{streamKey}/index.m3u8`(nginx·Caddy → MediaMTX) | [index.html](web/index.html), [broadcast.html](web/broadcast.html), [media/nginx.conf](media/nginx.conf) — 다른 기기 실재생은 D9 |
| 방송 중/종료 상태 관리 + 종료 시 송출 중단 | DB `status` READY→LIVE→ENDED(역행 409). **MediaMTX가 진실 공급원**: 백엔드가 2초마다 송출 목록을 조회해 송출이 들어오면 LIVE, 끊긴 채 30초(유예)면 ENDED, 종료된 방송에 남은 송출은 kick. 소유자 종료 = `POST /stop` → 송출 끊김. 같은 방송 중복 송출 거부(`overridePublisher: no`) | [StreamStatusSync](server/src/main/java/com/livestream/stream/StreamStatusSync.java), [Broadcast.goLive/end](server/src/main/java/com/livestream/broadcast/Broadcast.java), [MediaMtxClient](server/src/main/java/com/livestream/stream/MediaMtxClient.java) — [StreamStatusSyncTest](server/src/test/java/com/livestream/stream/StreamStatusSyncTest.java), E2E [broadcast-publish.spec.js](tests/broadcast-publish.spec.js) |
| **영구 저장 + 재시작 후 조회** | PostgreSQL 저장 (인메모리 금지), compose `pgdata` 볼륨 | `broadcasts` 테이블 ([V1__init.sql](server/src/main/resources/db/migration/V1__init.sql)) — D3에서 `down`→`up` 후 조회 수동 확인 |

### 필수 — 사용자 인증 및 인가
| 체크리스트 | 구현 방법 | 위치 (D3 구현 완료) |
|---|---|---|
| 회원가입/로그인/로그아웃/상태관리 | 비번 **BCrypt** 해싱. access JWT(HS256, 15분) + refresh(불투명 랜덤, DB엔 SHA-256 해시, 14일, 재발급 시 회전). 로그아웃 = refresh 폐기 | `POST /api/auth/signup·login·refresh·logout`, `GET /api/auth/me` → [AuthController](server/src/main/java/com/livestream/auth/AuthController.java), [AuthService](server/src/main/java/com/livestream/auth/AuthService.java), [JwtProvider](server/src/main/java/com/livestream/auth/JwtProvider.java) |
| 비로그인=조회·시청 가능 / 등록·송출=로그인만 | Spring Security 필터 체인: `GET /api/broadcasts/**` 공개, 그 외 인증 필요(없으면 401). HLS 시청은 MediaMTX `:8888` 공개 | [SecurityConfig](server/src/main/java/com/livestream/config/SecurityConfig.java), [JwtAuthFilter](server/src/main/java/com/livestream/auth/JwtAuthFilter.java) |
| **수정·삭제·송출제어 시 서버에서 소유자 검증** | 모든 변경 메서드가 `requireOwner` 경유 → `broadcast.ownerId == 토큰의 사용자 id` 아니면 403. **송출 자체(WHIP)도** MediaMTX `authMethod: http` → 백엔드가 토큰·소유자·방송 상태 확인, 아니면 401 | `PATCH·DELETE /api/broadcasts/{id}`, `POST /api/broadcasts/{id}/stop` → [BroadcastService.requireOwner](server/src/main/java/com/livestream/broadcast/BroadcastService.java). 송출 → [StreamAuthService](server/src/main/java/com/livestream/stream/StreamAuthService.java), [StreamAuthController](server/src/main/java/com/livestream/stream/StreamAuthController.java), [mediamtx.yml](media/mediamtx.yml)·[compose.yml](compose.yml) |
| (검증) 비로그인/소유자/타인 | 통합 테스트(실 PostgreSQL, Testcontainers) + E2E(WHIP 직접 호출 401) | [BroadcastAuthorizationTest](server/src/test/java/com/livestream/broadcast/BroadcastAuthorizationTest.java), [StreamAuthTest](server/src/test/java/com/livestream/stream/StreamAuthTest.java), [AuthFlowTest](server/src/test/java/com/livestream/auth/AuthFlowTest.java), [JwtProviderTest](server/src/test/java/com/livestream/auth/JwtProviderTest.java), [broadcast-publish.spec.js](tests/broadcast-publish.spec.js) |

### 선택 — 가산점 (효율순: 댓글 → 녹화 → 도네이션)
| 체크리스트 | 구현 방법 | 비고 |
|---|---|---|
| 댓글 작성/조회/수정/삭제 + 작성자 권한 ✅ | REST CRUD, 화면은 3초 주기 갱신. 조회 공개 / 작성 로그인 / 수정·삭제 **작성자만**(방송 소유자도 남의 댓글 불가, 서버 403) | `GET·POST /api/broadcasts/{id}/comments`, `PATCH·DELETE /api/comments/{id}` → [CommentService.requireAuthor](server/src/main/java/com/livestream/comment/CommentService.java), 화면 [broadcast.html](web/broadcast.html), 테스트 [CommentTest](server/src/test/java/com/livestream/comment/CommentTest.java)·[broadcast-crud.spec.js](tests/broadcast-crud.spec.js) |
| 녹화 저장 / 목록·상세·재생 / 위치 영구저장 ✅ | MediaMTX `record: yes`(fMP4, `recordings` 볼륨) → 파일 완성 시 `runOnRecordSegmentComplete`([record-hook.sh](media/record-hook.sh))가 백엔드에 알림 → `recordings` 테이블에 **파일 위치·시작 시각·길이** 저장(같은 파일 중복 저장 안 함). 재생은 MediaMTX playback 서버(`/playback/get?...&format=mp4`). 목록·상세 공개, 삭제는 방송 소유자(파일도 삭제) | [RecordingService](server/src/main/java/com/livestream/recording/RecordingService.java), [RecordingController](server/src/main/java/com/livestream/recording/RecordingController.java), [V6__recordings.sql](server/src/main/resources/db/migration/V6__recordings.sql), 화면 [recordings.html](web/recordings.html)·[recording.html](web/recording.html)·상세의 '다시보기', 테스트 [RecordingTest](server/src/test/java/com/livestream/recording/RecordingTest.java)·[broadcast-publish.spec.js](tests/broadcast-publish.spec.js) |
| 도네이션: 테스트결제/서버검증/중복적립 방지/합계 | PG 테스트모드 + **멱등키**(중복 승인 1회만) + ledger | 멱등성 명시 채점 |

---

## 3. 일정 (2주, 불확실한 것 먼저)

**1주차 — 뼈대 + 가장 위험한 구간 선검증**
- D1: MediaMTX 띄우기 → 브라우저 WHIP **1송출-1시청(HLS)** 루프 검증 ← *제일 위험, 먼저*
- D2: **HTTPS 배포** 파이프라인 (영상이 배포환경에서 도는지 조기 확인)
- D3-4: 인증/인가 (회원가입·로그인·가드·소유권)
- D5: 방송 CRUD + DB 영구저장

**2주차 — 필수 완성 + 선택**
- D6-7: PC 송출(화면+소리+마이크 믹싱) 완성 ✅ (송출 인증·상태 자동 연동 포함)
- D8: 모바일 송출 + 상태관리/종료 처리 ✅ (D6에 함께 — 실기기 확인은 D9)
- D9: 교차기기 시청 검증 + 예외처리 + README
- D10-11: 선택기능 (댓글 → 녹화)
- D12: 도네이션(여유 시) + 버퍼
- D13: **최종 배포·테스트계정·README 점검** → **레포 public 확인** → 제출

> 평가 우선순위: **기능 완결성 > 인증/인가 > 코드품질 > 문서화 > 선택가산**. 필수 100% 먼저, 선택은 그다음.

---

## 4. 리스크 / 함정 (도메인 처음이면 여기서 터짐)
1. **HTTPS 아니면 카메라·화면 캡처 안 됨** — 배포·테스트 전부 HTTPS(localhost만 예외).
2. **인가를 프론트에서만 하면 0점** — 모든 변경 API 서버 검증 필수.
3. **배포 실패 = 평가 불가** — 배포를 1주차에, 막판 금지. 제출 1회.
4. **WebRTC NAT** — 대칭 NAT에서 연결 실패 가능. 공인 IP 서버면 대개 OK, 안 되면 coturn(TURN).
5. **상태 동기화** — 송출 끊김/브라우저 종료 시 방송이 LIVE로 남는 문제 → ✅ 주기 체크(2초) + 30초 유예로 ENDED 처리 (웹훅은 백엔드·MediaMTX 재시작 때 이벤트를 놓치므로 안 씀).
6. **민감정보 커밋 금지** — `.env`는 `.gitignore`, `.env.example`만 올림.

## 5. 제출 전 최종 체크
- [ ] 서비스 주소(HTTPS) 접속됨
- [ ] 테스트 계정 또는 "테스트 로그인" 동작
- [ ] README 4필수(주소·계정·실행·기능확인) 작성
- [ ] 레포 **public** 전환 확인
- [ ] Codemit 제출 시 **'공모전에도 내기' 체크**
- [ ] 요구사항 근거에 코드 위치 기입(이 문서 2장에서 복사)
