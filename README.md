# live-streaming-platform

치지직 같은 실시간 라이브 방송 송출·시청 웹 서비스. (Codemit 공모전 제출작)

> 상태: **개발 중** · 마감 2026-11-19 23:59 · 평가 기준·계획은 [PLAN.md](./PLAN.md) 참고

---

## 심사자용 안내 (제출 전 반드시 채움)

> ⚠️ 아래 항목은 README 필수 요구사항. **빈칸이면 평가 불가**(서비스 접속·테스트 로그인 불가 시 평가 반영됨).

- **서비스 주소:** _(배포 URL, HTTPS)_ — TODO
- **테스트 계정:** _(계정 정보 또는 "테스트 로그인" 버튼 이용법)_ — TODO
- **실행 방법:** 실행 환경 + 환경변수 예시 → [.env.example](./.env.example) — TODO
- **기능 확인:** PC/모바일 **송출·시청·종료** 절차와 지원 환경 — TODO
- 시연 영상(선택): TODO

## 기술 스택

- 미디어: **MediaMTX** (WHIP 수신 → HLS/WHEP 출력, 녹화) · 브라우저 WebRTC
- 백엔드 / 프론트 / DB: [PLAN.md](./PLAN.md)의 "기술 결정" 참고
- 문서: [docs/streaming-tech-reference.md](./docs/streaming-tech-reference.md)

## 출처·라이선스

- 오픈소스/참고 코드 사용 시 이 섹션에 출처 명시 (공모전 유의사항).
