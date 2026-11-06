// Headless Edge e2e: publish.html (fake screen+mic) -> WHIP -> MediaMTX -> HLS -> watch.html
// 사전 준비: MediaMTX(media/mediamtx.yml) + `python -m http.server 8000 -d web` 실행 중. 사용: node scripts/spike-d1-e2e.mjs
// 가짜 화면·마이크(Chromium 플래그)라 실제 화면공유 권한창·실제 소리는 검증하지 않는다.
import { spawn } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
const prof = mkdtempSync(tmpdir() + '/spike-edge-');
const BROWSER = process.env.BROWSER ?? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const edge = spawn(BROWSER, [
  '--headless=new', '--remote-debugging-port=9333', `--user-data-dir=${prof}`,
  '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream',
  '--auto-select-desktop-capture-source=Entire screen', '--autoplay-policy=no-user-gesture-required', 'about:blank'], { stdio: 'ignore' });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const assert = (c, m) => { if (!c) throw new Error('FAIL: ' + m); console.log('PASS: ' + m); };

async function tab(url) {
  let t; for (let i = 0; i < 30 && !t; i++) { try { t = await (await fetch('http://127.0.0.1:9333/json/new?' + url, { method: 'PUT' })).json(); } catch { await sleep(300); } }
  const ws = new WebSocket(t.webSocketDebuggerUrl); await new Promise(r => ws.onopen = r);
  let id = 0; const pending = {};
  ws.onmessage = (e) => { const m = JSON.parse(e.data); pending[m.id]?.(m); };
  const send = (method, params) => new Promise(r => { pending[++id] = r; ws.send(JSON.stringify({ id, method, params })); });
  const ev = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, userGesture: true, returnByValue: true })).result?.result?.value;
  await sleep(1500);
  return ev;
}
const paths = async () => (await (await fetch('http://127.0.0.1:9997/v3/paths/list')).json()).items;

try {
  const pub = await tab('http://localhost:8000/publish.html');
  await pub("document.getElementById('key').value='e2e'; document.getElementById('start').click(); 1");
  await sleep(12000);
  console.log('--- publish log ---\n' + await pub("document.getElementById('log').textContent"));
  assert(await pub("document.getElementById('state').textContent") === 'connected', 'publisher RTCPeerConnection connected');
  const p = (await paths()).find(x => x.name === 'live/e2e');
  assert(p?.ready, 'MediaMTX path live/e2e ready');
  console.log('tracks:', JSON.stringify(p.tracks2 ?? p.tracks), 'bytesReceived:', p.bytesReceived);

  const m3u8 = await (await fetch('http://localhost:8888/live/e2e/index.m3u8')).text();
  console.log('--- master m3u8 ---\n' + m3u8);
  assert(m3u8.startsWith('#EXTM3U'), 'HLS master playlist served');

  const watch = await tab('http://localhost:8000/watch.html');
  await watch("document.getElementById('key').value='e2e'; document.getElementById('play').click(); 1");
  await sleep(6000);
  const t1 = await watch("document.getElementById('video').currentTime");
  await sleep(2000);
  const t2 = await watch("document.getElementById('video').currentTime");
  const dims = await watch("const v=document.getElementById('video'); v.videoWidth+'x'+v.videoHeight");
  console.log('--- watch log ---\n' + await watch("document.getElementById('log').textContent"));
  console.log(`watch currentTime ${t1} -> ${t2}, video ${dims}`);
  assert(t2 > t1, 'watch.html video playing (currentTime advancing)');

  await pub("document.getElementById('stop').click(); 1");
  await sleep(1500);
  assert(!(await paths()).some(x => x.name === 'live/e2e'), 'stop -> WHIP DELETE -> path removed');
} catch (e) { console.log(e.message); process.exitCode = 1; }
finally { edge.kill(); process.exit(); }
