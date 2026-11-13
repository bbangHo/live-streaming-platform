// @ts-check
// 진짜 탭 캡처 → dev-publish.html(WHIP) → MediaMTX → HLS → dev-watch.html 를 한 바퀴 돌리고
// 소리 섞임 · 영상 내용 · 지연 · 탭 닫힘 시 자동 종료를 숫자로 판정한다.
//
// 진짜인 것: 탭 화면·탭 소리 캡처(getDisplayMedia), WHIP, MediaMTX, HLS, hls.js 재생
// 흉내낸 것: 마이크. getUserMedia를 1000Hz 발진기로 바꿔 넣는다(테스트 PC에 마이크가 없어도 돌게)
// 못 하는 것: 브라우저 공유 창 UI(실행 옵션으로 건너뜀), Safari/iOS
const { test, expect } = require('@playwright/test');

const TAB_HZ = 440;   // 캡처되는 탭이 내는 소리
const MIC_HZ = 1000;  // 가짜 마이크 소리
const TICK_MS = 50;   // 영상에 그리는 시계 한 칸
const KEY = 'pw' + Date.now();
// E2E_BASE 없으면 로컬 개발 서버(:8000 + MediaMTX 직접), 있으면 배포 주소 하나로 (예: https://localhost)
const BASE = process.env.E2E_BASE ?? 'http://localhost:8000';
const HLS = process.env.E2E_BASE ?? 'http://localhost:8888';

// 캡처 대상 탭: 440Hz를 틀고, 현재 시각(50ms 단위 16비트)을 흑백 블록 8x2로 그린다
const SOURCE_HTML = `<!doctype html><title>SPIKE-SOURCE</title>
<style>html,body{margin:0;height:100%;background:#000}canvas{width:100vw;height:100vh;display:block}</style>
<canvas id=c width=800 height=400></canvas><script>
const ctx = new AudioContext(), o = ctx.createOscillator(); o.frequency.value = ${TAB_HZ}; o.connect(ctx.destination); o.start();
const g = document.getElementById('c').getContext('2d');
setInterval(() => { const v = Math.floor(Date.now() / ${TICK_MS}) & 0xffff;
  for (let i = 0; i < 16; i++) { g.fillStyle = (v >> i) & 1 ? '#fff' : '#000'; g.fillRect((i % 8) * 100, Math.floor(i / 8) * 200, 100, 200); } }, 20);
</script>`;

// 시청 화면에서 블록 16개를 읽어 시각 값을 복원
const READ_TICK = `(() => { const v = document.getElementById('video'); const c = document.createElement('canvas');
  c.width = 800; c.height = 400; const g = c.getContext('2d'); g.drawImage(v, 0, 0, 800, 400); let n = 0;
  for (let i = 0; i < 16; i++) { const p = g.getImageData((i % 8) * 100 + 50, Math.floor(i / 8) * 200 + 100, 1, 1).data;
    if (p[0] + p[1] + p[2] > 384) n |= 1 << i; }
  return { tick: n, now: Math.floor(Date.now() / ${TICK_MS}) & 0xffff }; })()`;

// 방송 중이면 HLS 재생목록 200, 없으면 404 (Control API는 Docker에선 localhost 제한에 막혀 안 쓴다)
const hlsStatus = async (request) => (await request.get(`${HLS}/live/${KEY}/index.m3u8`)).status();

test('진짜 탭 캡처 송출 → HLS 시청 루프', async ({ context, request }) => {
  const source = await context.newPage();
  await source.setContent(SOURCE_HTML);

  const pub = await context.newPage();
  await pub.addInitScript((hz) => {
    navigator.mediaDevices.getUserMedia = async () => {
      const ctx = new AudioContext(), o = ctx.createOscillator(), d = ctx.createMediaStreamDestination();
      o.frequency.value = hz; o.connect(d); o.start(); return d.stream;
    };
  }, MIC_HZ);
  await pub.goto(`${BASE}/dev-publish.html`);
  await pub.fill('#key', KEY);
  await pub.click('#start');
  await expect(pub.locator('#state')).toHaveText('connected', { timeout: 20_000 });
  await expect(pub.locator('#log')).toContainText('화면 소리 1개, 마이크 1개');

  await expect.poll(() => hlsStatus(request), { timeout: 15_000 }).toBe(200);

  const watch = await context.newPage();
  await watch.goto(`${BASE}/dev-watch.html`);
  await watch.fill('#key', KEY);
  await watch.click('#play');
  await expect.poll(() => watch.evaluate(() => document.getElementById('video').currentTime), { timeout: 30_000 }).toBeGreaterThan(2);

  // 1) 소리: 탭 소리(440Hz)와 마이크(1000Hz)가 시청 쪽에서 둘 다 들리는가
  const spectrum = await watch.evaluate(async ([a, b]) => {
    const v = document.getElementById('video'); v.muted = false;
    const ctx = new AudioContext(); await ctx.resume();
    const an = ctx.createAnalyser(); an.fftSize = 8192;
    ctx.createMediaElementSource(v).connect(an);
    // 한 번 찍은 스펙트럼은 흔들리므로 소리가 들어오기 시작한 뒤 2초(20번) 평균을 낸다
    const f = new Float32Array(an.frequencyBinCount), bin = (hz) => Math.round(hz / (ctx.sampleRate / an.fftSize));
    const sum = { tab: 0, mic: 0, off: 0 }; let n = 0;
    for (let i = 0; i < 100 && n < 20; i++) {
      await new Promise(r => setTimeout(r, 100));
      an.getFloatFrequencyData(f);
      if (!isFinite(f[bin(b)])) continue; // 아직 소리가 안 들어옴(버퍼링 등)
      sum.tab += f[bin(a)]; sum.mic += f[bin(b)]; sum.off += f[bin(700)]; n++; // 700Hz = 두 소리 사이, 잡음 기준
    }
    return { tab: sum.tab / n, mic: sum.mic / n, off: sum.off / n, samples: n };
  }, [TAB_HZ, MIC_HZ]);
  console.log('spectrum dB', spectrum);
  expect(spectrum.samples, '시청 쪽에서 소리가 2초 이상 들어옴').toBe(20);
  // 정상: 탭 약 -38dB, 마이크 약 -18dB, 잡음 약 -83dB. --mute-audio로 탭을 끄면 탭이 -93dB로 떨어져 이 검사에 걸린다(확인함)
  expect(spectrum.tab, '탭 소리 440Hz').toBeGreaterThan(Math.max(-70, spectrum.off + 20));
  expect(spectrum.mic, '마이크 1000Hz').toBeGreaterThan(Math.max(-70, spectrum.off + 20));

  // 2) 영상 내용 + 지연: 탭에 그린 시계를 시청 화면에서 읽는다
  const reads = [];
  for (let i = 0; i < 3; i++) { reads.push(await watch.evaluate(READ_TICK)); await watch.waitForTimeout(1000); }
  const latencies = reads.map(r => ((r.now - r.tick + 0x10000) & 0xffff) * TICK_MS);
  console.log('glass-to-glass latency ms', latencies);
  for (const ms of latencies) expect(ms, '지연(ms)이 0~15초 범위 = 시계를 제대로 읽음').toBeLessThan(15_000);
  const advanced = ((reads[2].tick - reads[0].tick + 0x10000) & 0xffff) * TICK_MS;
  // 멈춘 화면이면 0. LL-HLS가 1초쯤 버퍼링하는 경우가 있어(1/5회 관찰) 기준은 0.5초
  expect(advanced, '2초 동안 영상 속 시계가 흘렀다 = 라이브').toBeGreaterThan(500);

  // 3) 캡처 중인 탭을 닫으면(= 공유 중지) 송출이 스스로 끝나는가
  await source.close();
  await expect(pub.locator('#state')).toHaveText('종료', { timeout: 10_000 });
  await expect.poll(() => hlsStatus(request), { timeout: 10_000 }).toBe(404);
});
