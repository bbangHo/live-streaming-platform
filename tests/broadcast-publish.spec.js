// @ts-check
// 실제 방송 흐름 E2E: 로그인 → 방송 만들기 → PC 송출 페이지에서 진짜 탭 캡처 송출 → 서버가 자동으로 '방송 중'
// → 비로그인 시청자가 상세 페이지에서 재생 → 타인 토큰·토큰 없이 WHIP 송출 거부(401) → 소유자 '방송 종료' → 송출 끊김·'종료'
// + 모바일 송출 페이지(가짜 카메라)로 송출 → 방송 중 → 종료.
// 백엔드·MediaMTX 송출 인증이 필요하므로 `docker compose up -d` 상태(http://localhost:8000)에서만 돈다.
const { test, expect } = require('@playwright/test');

const BASE = process.env.E2E_BASE || 'http://localhost:8000';

test.beforeAll(async ({ request }) => {
  const res = await request.get(`${BASE}/api/broadcasts`).catch(() => null);
  test.skip(!res?.ok(), '백엔드 없음 — docker compose up -d 후 실행');
});

// 캡처 대상 탭 (playwright.config의 --auto-select-tab-capture-source-by-title=SPIKE-SOURCE). 소리 + 움직이는 화면
const SOURCE_HTML = `<!doctype html><title>SPIKE-SOURCE</title><body style="margin:0;background:#000">
<canvas id=c width=640 height=360 style="width:100vw;height:100vh"></canvas><script>
const ctx = new AudioContext(), o = ctx.createOscillator(); o.connect(ctx.destination); o.start();
const g = document.getElementById('c').getContext('2d'); let x = 0;
setInterval(() => { g.fillStyle = '#000'; g.fillRect(0, 0, 640, 360); g.fillStyle = '#0f0'; g.fillRect(x = (x + 8) % 640, 140, 80, 80); }, 33);
</script>`;

async function loginAs(page, username, password) {
  await page.goto(`${BASE}/login.html`);
  const form = page.locator('#login');
  await form.getByPlaceholder('아이디').fill(username);
  await form.getByPlaceholder('비밀번호').fill(password);
  await form.getByRole('button', { name: '로그인' }).click();
  await expect(page.locator('nav .me')).toHaveText(username);
}

async function signupAndCreate(page, title) {
  const name = `pub_${Date.now()}`;
  await page.goto(`${BASE}/login.html`);
  const signup = page.locator('#signup');
  await signup.getByPlaceholder(/아이디/).fill(name);
  await signup.getByPlaceholder(/비밀번호/).fill('password123');
  await signup.getByRole('button', { name: '가입하고 로그인' }).click();
  await expect(page.locator('nav .me')).toHaveText(name);
  await page.getByPlaceholder('방송 제목').fill(title);
  await page.getByRole('button', { name: '만들기' }).click();
  await expect(page.locator('#title')).toHaveText(title);
  const id = new URL(page.url()).searchParams.get('id');
  const b = await (await page.request.get(`${BASE}/api/broadcasts/${id}`)).json();
  return { id, key: b.streamKey };
}

const fakeMic = (hz) => {
  navigator.mediaDevices.getUserMedia = async (c) => {
    const ctx = new AudioContext(), o = ctx.createOscillator(), d = ctx.createMediaStreamDestination();
    o.frequency.value = hz; o.connect(d); o.start();
    if (c && c.video) { // 모바일 페이지용 가짜 카메라: 캔버스 영상 + 발진기 소리
      const cv = Object.assign(document.createElement('canvas'), { width: 640, height: 360 }), g = cv.getContext('2d');
      let x = 0; setInterval(() => { g.fillStyle = '#00f'; g.fillRect(0, 0, 640, 360); g.fillStyle = '#ff0'; g.fillRect(x = (x + 8) % 640, 140, 80, 80); }, 33);
      const s = cv.captureStream(30);
      if (c.audio) s.addTrack(d.stream.getAudioTracks()[0]);
      return s;
    }
    return d.stream;
  };
};

test('PC 송출 → 자동 방송 중 → 비로그인 시청 → 타인 송출 거부 → 소유자 종료', async ({ browser, context, request }) => {
  const page = await context.newPage();
  const title = `E2E 송출 ${Date.now()}`;
  const { id, key } = await signupAndCreate(page, title);
  const whipUrl = `${BASE}/live/${key}/whip`;

  // 토큰 없이, 타인 토큰으로 WHIP 송출 → 401 (스트림 키는 공개 값이지만 송출은 못 함)
  const offer = 'v=0\r\n';
  expect((await request.post(whipUrl, { headers: { 'Content-Type': 'application/sdp' }, data: offer })).status()).toBe(401);
  const demo2 = await (await request.post(`${BASE}/api/auth/login`, { data: { username: 'demo2', password: 'demo1234' } })).json();
  expect((await request.post(whipUrl, { headers: { 'Content-Type': 'application/sdp', Authorization: `Bearer ${demo2.accessToken}` }, data: offer })).status()).toBe(401);

  // 소유자 PC 송출
  const source = await context.newPage();
  await source.setContent(SOURCE_HTML);
  await page.addInitScript(fakeMic, 1000);
  await page.goto(`${BASE}/broadcast.html?id=${id}`);
  await page.getByRole('link', { name: 'PC 화면 송출' }).click();
  await expect(page).toHaveURL(/publish-pc\.html\?id=/);
  await page.getByRole('button', { name: '방송 시작' }).click();
  await expect(page.locator('#conn')).toHaveText('송출 중', { timeout: 20_000 });
  await expect(page.locator('#log')).toContainText('화면 소리 1개, 마이크 1개');
  await expect(page.locator('#status')).toHaveText('방송 중', { timeout: 10_000 }); // 서버가 MediaMTX를 보고 자동 전환

  // 같은 방송에 두 번째 송출 → 거부 (소유자 토큰이어도)
  const owner = await page.evaluate(() => JSON.parse(localStorage.getItem('ls.tokens')).accessToken);
  expect((await request.post(whipUrl, { headers: { 'Content-Type': 'application/sdp', Authorization: `Bearer ${owner}` }, data: offer })).status()).not.toBe(201);

  // 비로그인 시청자: 목록 '방송 중' 탭 → 상세에서 영상 재생
  const viewer = await browser.newContext();
  const watch = await viewer.newPage();
  await watch.goto(`${BASE}/index.html`);
  await watch.getByRole('button', { name: '방송 중' }).click();
  await watch.getByText(title).click();
  await expect(watch.locator('#manage')).toBeHidden();
  await expect.poll(() => watch.evaluate(() => document.querySelector('#player video')?.currentTime ?? 0), { timeout: 30_000 }).toBeGreaterThan(1);

  // 소유자 '방송 종료' → 종료, 송출 끊김, HLS 사라짐, 시청 화면 '종료'
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: '방송 종료' }).click();
  await expect(page.locator('#status')).toHaveText('종료');
  await expect(page.locator('#start')).toBeDisabled();
  await expect.poll(async () => (await request.get(`${BASE}/live/${key}/index.m3u8`)).status(), { timeout: 15_000 }).toBe(404);
  await expect(watch.locator('#status')).toHaveText('종료', { timeout: 10_000 });
  // 종료된 방송에는 소유자도 다시 송출 못 함
  expect((await request.post(whipUrl, { headers: { 'Content-Type': 'application/sdp', Authorization: `Bearer ${owner}` }, data: offer })).status()).toBe(401);
  await viewer.close();
});

test('모바일 송출(카메라) → 방송 중 → 종료', async ({ context }) => {
  const page = await context.newPage();
  const { id } = await signupAndCreate(page, `E2E 모바일 ${Date.now()}`);
  await page.addInitScript(fakeMic, 1000);
  await page.goto(`${BASE}/publish-mobile.html?id=${id}`);
  await page.getByRole('button', { name: '방송 시작' }).click();
  await expect(page.locator('#conn')).toHaveText('송출 중', { timeout: 20_000 });
  await expect(page.locator('#log')).toContainText('카메라 1개');
  await expect(page.locator('#log')).toContainText('마이크 1개');
  await expect(page.locator('#status')).toHaveText('방송 중', { timeout: 10_000 });
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: '방송 종료' }).click();
  await expect(page.locator('#status')).toHaveText('종료');
});

test('비로그인·타인은 송출 페이지를 쓸 수 없다', async ({ page }) => {
  await page.goto(`${BASE}/publish-pc.html?id=1`);
  await expect(page).toHaveURL(/login\.html\?next=/);
  await loginAs(page, 'demo2', 'demo1234'); // 1번 = demo1의 시연 방송
  await page.goto(`${BASE}/publish-pc.html?id=1`);
  await expect(page.locator('#error')).toHaveText('이 방송의 소유자만 송출할 수 있습니다.');
  await expect(page.locator('#start')).toBeDisabled();
});
