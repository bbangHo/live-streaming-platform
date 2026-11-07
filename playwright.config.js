// @ts-check
const { defineConfig } = require('@playwright/test');

const mediamtx = process.platform === 'win32' ? 'media\\mediamtx.exe' : './media/mediamtx';

module.exports = defineConfig({
  testDir: 'tests',
  timeout: 90_000,
  use: {
    ignoreHTTPSErrors: true, // 로컬 Caddy(https://localhost)는 Caddy 내부 인증서
    channel: 'msedge', // 설치된 Edge 사용 → 브라우저 따로 안 받아도 됨. Chrome이면 'chrome'
    launchOptions: {
      ignoreDefaultArgs: ['--mute-audio'], // Playwright 기본값은 탭 소리를 꺼서 탭 오디오 캡처가 무음이 된다
      args: [
        // 공유 창을 건너뛰고 제목이 맞는 "진짜 탭"을 캡처 (가짜 장치 아님)
        '--auto-select-tab-capture-source-by-title=SPIKE-SOURCE',
        '--autoplay-policy=no-user-gesture-required',
      ],
    },
  },
  // 이미 켜 둔 서버가 있으면 그대로 쓴다. E2E_BASE(배포 주소)로 돌릴 땐 띄우지 않는다
  webServer: process.env.E2E_BASE ? [] : [
    { command: `${mediamtx} media/mediamtx.yml`, url: 'http://localhost:8888/live/ready/', reuseExistingServer: true },
    { command: 'python -m http.server 8000 --bind 127.0.0.1 -d web', url: 'http://localhost:8000/publish.html', reuseExistingServer: true },
  ],
});
