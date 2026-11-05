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
| 백엔드 | **권장: NestJS(TS)** 또는 Spring Boot — *팀 선택, 조정 가능* | 조정 |
| 프론트 | **권장: React/Next + hls.js** | 조정 |
| DB | **PostgreSQL** (영구 저장 필수) | 거의 고정 |
| 배포 | **HTTPS 필수** (getUserMedia는 secure context 전용) | **고정** |

> **왜 미디어 레이어가 고정인가:** 브라우저는 RTMP를 못 쏜다 → 웹 송출은 WebRTC(WHIP)가 유일. 시청 확장은 HLS. 이 둘을 한 통으로 잇는 게 MediaMTX. 백엔드 언어만 자유.

---

## 2. 체크리스트 ↔ 구현 매핑

> 각 항목의 "구현 위치"는 계획이며, 구현하면서 실제 파일·엔드포인트로 갱신한다. ✅표시는 제출 시 '충족' 체크 대상.

### 필수 — PC 라이브 방송
| 체크리스트 | 구현 방법 | (계획) 위치 |
|---|---|---|
| 화면 캡처 실시간 송출 | `getDisplayMedia()` → WHIP로 MediaMTX 송출 | `web/src/broadcast/pc/usePcStream.ts` |
| 탭/시스템 소리 + 마이크 함께 | `getDisplayMedia({audio:true})` + `getUserMedia(audio)` → Web Audio로 믹싱 | 동上 |
| 마이크 음성 송출 | 위 믹싱 트랙 포함 | 동上 |
| 시작/종료 시 연결 생성·종료 | WHIP 세션 open/close + 서버에 상태 PATCH | `api/broadcasts/:id/start`·`/stop` |

### 필수 — 모바일 라이브 방송
| 체크리스트 | 구현 방법 | (계획) 위치 |
|---|---|---|
| 카메라+마이크 실시간 송출 | `getUserMedia({video,audio})` → 같은 WHIP 경로 재사용 | `web/src/broadcast/mobile/useMobileStream.ts` |
| 마이크 음성 함께 | audio 트랙 포함 | 동上 |
| 시작·종료 | PC와 동일 start/stop 흐름 | 공통 `useWhipPublisher` |

### 필수 — 방송 관리 및 시청
| 체크리스트 | 구현 방법 | (계획) 위치 |
|---|---|---|
| 등록·조회·수정·삭제 | REST CRUD | `api/broadcasts` (controller/service) |
| 목록·상세 + 다른 기기 수신·재생 | 목록/상세 페이지 + hls.js 플레이어(HLS URL) | `web/src/pages/broadcasts/*` |
| 방송 중/종료 상태 관리 + 종료 시 송출 중단 | DB `status`(LIVE/ENDED) + stop 시 WHIP 종료 + (MediaMTX webhook로 동기화) | `broadcast.status`, `/stop`, webhook |
| **영구 저장 + 재시작 후 조회** | PostgreSQL 저장 (인메모리 금지) | `broadcasts` 테이블 |

### 필수 — 사용자 인증 및 인가
| 체크리스트 | 구현 방법 | (계획) 위치 |
|---|---|---|
| 회원가입/로그인/로그아웃/상태관리 | 비번 **argon2/bcrypt** 해싱 + JWT(access/refresh) 또는 세션쿠키 | `api/auth/*` |
| 비로그인=조회·시청 가능 / 등록·송출=로그인만 | 라우트 가드(인증 미들웨어) | `middleware/auth`, 라우트별 적용 |
| **수정·삭제·송출제어 시 서버에서 소유자 검증** | 각 변경 API에서 `broadcast.ownerId === req.user.id` 재확인, 아니면 403 | `broadcasts.service` 전 변경 메서드 |

### 선택 — 가산점 (효율순: 댓글 → 녹화 → 도네이션)
| 체크리스트 | 구현 방법 | 비고 |
|---|---|---|
| 댓글 작성/조회/수정/삭제 + 작성자 권한 | **REST CRUD** (실시간 불필요 — 루브릭이 요구 안 함) | 제일 쌈 |
| 녹화 저장 / 목록·상세·재생 / 위치 영구저장 | **MediaMTX record 옵션** → 파일 + DB에 경로 | MediaMTX로 거의 공짜 |
| 도네이션: 테스트결제/서버검증/중복적립 방지/합계 | PG 테스트모드 + **멱등키**(중복 승인 1회만) + ledger | 멱등성 명시 채점 |

---

## 3. 일정 (2주, 불확실한 것 먼저)

**1주차 — 뼈대 + 가장 위험한 구간 선검증**
- D1: MediaMTX 띄우기 → 브라우저 WHIP **1송출-1시청(HLS)** 루프 검증 ← *제일 위험, 먼저*
- D2: **HTTPS 배포** 파이프라인 (영상이 배포환경에서 도는지 조기 확인)
- D3-4: 인증/인가 (회원가입·로그인·가드·소유권)
- D5: 방송 CRUD + DB 영구저장

**2주차 — 필수 완성 + 선택**
- D6-7: PC 송출(화면+소리+마이크 믹싱) 완성
- D8: 모바일 송출 + 상태관리/종료 처리
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
5. **상태 동기화** — 송출 끊김/브라우저 종료 시 방송이 LIVE로 남는 문제 → MediaMTX webhook 또는 주기 체크로 ENDED 처리.
6. **민감정보 커밋 금지** — `.env`는 `.gitignore`, `.env.example`만 올림.

## 5. 제출 전 최종 체크
- [ ] 서비스 주소(HTTPS) 접속됨
- [ ] 테스트 계정 또는 "테스트 로그인" 동작
- [ ] README 4필수(주소·계정·실행·기능확인) 작성
- [ ] 레포 **public** 전환 확인
- [ ] Codemit 제출 시 **'공모전에도 내기' 체크**
- [ ] 요구사항 근거에 코드 위치 기입(이 문서 2장에서 복사)
