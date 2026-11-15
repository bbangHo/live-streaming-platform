# roadmap.md — task 목록·진행 상태 (라이브)

필수 기능 우선. 모델 가이드: 위험 task(인증·인가·돈·상태전이) = Opus high, 화면·CRUD = Sonnet/Opus medium, 재시도 때 한 단계 ↑. 하위 세션은 **PR만, 머지 금지**.

## 상태
| id | task | 선행 | 모델·effort | 상태 |
|---|---|---|---|---|
| D1 | WHIP 송출 → MediaMTX → HLS 시청 스파이크 | — | Opus high | ✅ 머지(PR #1) |
| D2 | 로컬 `docker compose` 스택 + 심사자 README | D1 | Opus medium | ✅ 머지(PR #2) |
| D3 | 인증/인가 (Spring Boot·JWT·소유자검증) | D2 | Opus high | ✅ 머지(PR #3) |
| D5 | 방송 CRUD + 목록·상세·시청·관리 화면 | D3 | Opus medium | ✅ 머지(PR #4) |
| D6 | PC 송출 ↔ 방송 상태(LIVE/ENDED) 연동 | D5 | Opus high | 🔶 PR #6 열림(머지 대기: 실브라우저 화면공유 수동확인 후 사용자 머지). PR #5는 #6으로 대체해 닫음 |
| D8 | 모바일 카메라 송출 | D6 | Opus medium | 🔶 PR #6 (실기기 미확인) |
| D-state | 방송 상태관리 마무리(종료 시 송출 중단·동기화) | D6 | Opus high | 🔶 PR #6 (2초 동기화·30초 유예·kick) |
| D-readme | README 제출 필수항목(주소·테스트계정·실행·기능확인) | 필수기능 | medium | ⬜ |
| O-댓글 | (선택) 댓글 REST CRUD + 작성자 권한 | D5 | medium | 🔶 PR #6 |
| O-녹화 | (선택) 녹화·다시보기(MediaMTX record→저장) | D5 | medium | 🔶 PR #6 |
| O-도네 | (선택) 도네이션(토스 테스트결제·멱등·원장) | D5 | Opus high | 🔶 PR #6 (토스 키 없이 내장 테스트 PG로 검증) |
| D-deploy | 공인 URL 배포(HTTPS) — 제출 임박 | 필수기능 | — | ⬜ |

## 확인 대기 (사용자 결정 필요)
- **PR #6 머지** — 사용자가 #6 채택 결정(11-15), #5 닫음. 머지 전 실브라우저 화면공유·「오디오 공유」 송출 → 상세 시청 수동확인.
- PR #6 가정(되돌릴 수 있음): 송출 페이지 PC/모바일 분리(사용자 결정 C안), `POST /start` 제거(송출=시작), 유예 30초(사용자 결정), 개발용 우회(`STREAM_DEV_BYPASS`, 사용자 결정 유지), 토스 키 없으면 내장 테스트 PG.
- 토스페이먼츠 **테스트 키** 발급·입력(외부계정) — 넣으면 실제 토스 결제창으로 확인 가능. 없어도 내장 테스트 PG로 동작.
- PR #6 `docker compose up -d --build`로 백엔드 이미지 빌드 확인(클라우드에선 컨테이너 안 Maven 차단으로 미검증).
- ~~관례 미준수(에러 형식 ProblemDetail, 토큰 localStorage)~~ → 사용자 결정(11-15): 지금 안 고치고 README "알려진 한계"에 기록.
- 프론트 프레임워크 확정(현재 정적 HTML; React 도입 여부).
- 브랜드색 마젠타 핑크 `#FF3D8B` 확정 여부(FigCraft 디자인 쪽).
- 공인 배포 타깃(minipc vs 클라우드).

## 메모
- 클라우드 세션 '작업 이어가기'(치지직 클라우드)에서 모바일로 이어감. 네트워크 egress allowlist 필요(github/docker/gradle/npm).
- FigCraft 디자인 복제는 로컬 전용(클라우드 불가).
