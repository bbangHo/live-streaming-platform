// @ts-check
// 화면으로 하는 방송 관리 E2E: 가입 → 방송 만들기 → 목록·상세 → 수정 → 송출 링크 → 타인·비로그인 화면과 서버 거부 → 삭제
// 백엔드가 필요하므로 `docker compose up -d` 상태(http://localhost:8000)에서 돈다. 백엔드가 없으면 건너뛴다.
const { test, expect } = require('@playwright/test');

const BASE = process.env.E2E_BASE || 'http://localhost:8000';

test.beforeAll(async ({ request }) => {
  const res = await request.get(`${BASE}/api/broadcasts`).catch(() => null);
  test.skip(!res?.ok(), '백엔드 없음 — docker compose up -d 후 실행');
});

async function login(page, username, password) {
  await page.goto(`${BASE}/login.html`);
  const form = page.locator('#login');
  await form.getByPlaceholder('아이디').fill(username);
  await form.getByPlaceholder('비밀번호').fill(password);
  await form.getByRole('button', { name: '로그인' }).click();
  await expect(page.locator('nav .me')).toHaveText(username);
}

test('방송 관리: 소유자만 화면·서버 모두 변경 가능', async ({ page, request }) => {
  const name = `e2e_${Date.now()}`;
  const title = `E2E 방송 ${Date.now()}`;

  // 가입하고 로그인
  await page.goto(`${BASE}/login.html`);
  const signup = page.locator('#signup');
  await signup.getByPlaceholder(/아이디/).fill(name);
  await signup.getByPlaceholder(/비밀번호/).fill('password123');
  await signup.getByRole('button', { name: '가입하고 로그인' }).click();
  await expect(page.locator('nav .me')).toHaveText(name);

  // 방송 만들기 → 상세로 이동
  await page.getByPlaceholder('방송 제목').fill(title);
  await page.getByPlaceholder('설명 (선택)').fill('E2E 설명');
  await page.getByRole('button', { name: '만들기' }).click();
  await expect(page.locator('#title')).toHaveText(title);
  await expect(page.locator('#manage')).toBeVisible();
  const id = new URL(page.url()).searchParams.get('id');

  // 수정
  await page.locator('#edit input[name=title]').fill(`${title} (수정)`);
  await page.getByRole('button', { name: '수정 저장' }).click();
  await expect(page.locator('#title')).toHaveText(`${title} (수정)`);

  // 목록 '전체'에 대기 상태로 보이고, 소유자 관리 영역에 PC·모바일 송출 링크가 있다 (송출·상태 전이는 broadcast-publish.spec.js)
  await expect(page.locator('#status')).toHaveText('대기');
  await expect(page.getByRole('link', { name: 'PC 화면 송출' })).toHaveAttribute('href', `publish-pc.html?id=${id}`);
  await expect(page.getByRole('link', { name: '모바일 카메라 송출' })).toHaveAttribute('href', `publish-mobile.html?id=${id}`);
  await page.goto(`${BASE}/index.html`);
  await expect(page.locator('#list')).toContainText(`${title} (수정)`);
  await page.goto(`${BASE}/broadcast.html?id=${id}`);

  // 비로그인: 상세는 보이지만 관리 영역 없음, API로 직접 수정하면 401
  await page.getByRole('button', { name: '로그아웃' }).click();
  await expect(page).toHaveURL(/index\.html$/);
  await page.goto(`${BASE}/broadcast.html?id=${id}`);
  await expect(page.locator('#title')).toHaveText(`${title} (수정)`);
  await expect(page.locator('#manage')).toBeHidden();
  expect((await request.patch(`${BASE}/api/broadcasts/${id}`, { data: { title: 'x' } })).status()).toBe(401);

  // 타인(demo2): 관리 영역 없음, 토큰으로 직접 삭제해도 403
  await login(page, 'demo2', 'demo1234');
  await page.goto(`${BASE}/broadcast.html?id=${id}`);
  await expect(page.locator('#title')).toHaveText(`${title} (수정)`);
  await expect(page.locator('#manage')).toBeHidden();
  const demo2 = await page.evaluate(() => JSON.parse(localStorage.getItem('ls.tokens')).accessToken);
  const del = await request.delete(`${BASE}/api/broadcasts/${id}`, { headers: { Authorization: `Bearer ${demo2}` } });
  expect(del.status()).toBe(403);

  // 소유자가 삭제 → 목록에서 사라짐
  await page.getByRole('button', { name: '로그아웃' }).click();
  await expect(page).toHaveURL(/index\.html$/);
  await login(page, name, 'password123');
  await page.goto(`${BASE}/broadcast.html?id=${id}`);
  page.once('dialog', (d) => d.accept());
  await page.getByRole('button', { name: '삭제' }).click();
  await expect(page).toHaveURL(/index\.html$/);
  await expect(page.locator('#list')).not.toContainText(title);
});
