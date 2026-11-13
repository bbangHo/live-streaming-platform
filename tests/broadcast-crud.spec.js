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

test('댓글: 비로그인 조회, 로그인 작성, 작성자만 수정·삭제', async ({ browser, page, request }) => {
  // demo1의 시연 방송(1번)에 demo2가 댓글
  await login(page, 'demo2', 'demo1234');
  await page.goto(`${BASE}/broadcast.html?id=1`);
  const text = `E2E 댓글 ${Date.now()}`;
  await page.getByPlaceholder(/댓글 쓰기/).fill(text);
  await page.getByRole('button', { name: '등록' }).click();
  const mine = page.locator('#comment-list li', { hasText: text });
  await expect(mine).toContainText('demo2');

  // 비로그인: 보이지만 입력창·수정 버튼 없음
  const anon = await browser.newPage();
  await anon.goto(`${BASE}/broadcast.html?id=1`);
  await expect(anon.locator('#comment-list')).toContainText(text);
  await expect(anon.locator('#comment-form')).toBeHidden();
  await expect(anon.locator('#comment-list li', { hasText: text }).getByRole('button', { name: '수정' })).toHaveCount(0);

  // 방송 소유자(demo1)도 남의 댓글은 못 고침: 버튼 없음 + API 403
  const owner = await (await request.post(`${BASE}/api/auth/login`, { data: { username: 'demo1', password: 'demo1234' } })).json();
  const list = await (await request.get(`${BASE}/api/broadcasts/1/comments`)).json();
  const cid = list.find((c) => c.content === text).id;
  expect((await request.patch(`${BASE}/api/comments/${cid}`, { headers: { Authorization: `Bearer ${owner.accessToken}` }, data: { content: 'x' } })).status()).toBe(403);
  expect((await request.delete(`${BASE}/api/comments/${cid}`)).status()).toBe(401);

  // 작성자: 수정 → (수정됨), 삭제 → 사라짐 (다른 화면에도 3초 안에 반영)
  await mine.getByRole('button', { name: '수정' }).click();
  await page.locator('#comment-list .editing input').fill(`${text} 고침`);
  await page.getByRole('button', { name: '저장' }).click();
  await expect(page.locator('#comment-list')).toContainText(`${text} 고침`);
  await expect(page.locator('#comment-list li', { hasText: text })).toContainText('(수정됨)');
  await expect(anon.locator('#comment-list')).toContainText(`${text} 고침`, { timeout: 5000 });
  page.once('dialog', (d) => d.accept());
  await page.locator('#comment-list li', { hasText: text }).getByRole('button', { name: '삭제' }).click();
  await expect(page.locator('#comment-list')).not.toContainText(text);
  await expect(anon.locator('#comment-list')).not.toContainText(text, { timeout: 5000 });
  await anon.close();
});

test('후원: 테스트 결제 → 서버 승인 → 합계, 새로고침해도 한 번만 적립, 카드 거절·금액 조작은 적립 안 됨', async ({ page, request }) => {
  const summary = async () => (await request.get(`${BASE}/api/broadcasts/1/donations`)).json();
  const before = (await summary()).total;

  // demo2가 demo1의 시연 방송(1번)에 5,000원 후원
  await login(page, 'demo2', 'demo1234');
  await page.goto(`${BASE}/broadcast.html?id=1`);
  const message = `E2E 후원 ${Date.now()}`;
  await page.getByRole('button', { name: '5,000원' }).click();
  await page.getByPlaceholder('응원 메시지 (선택)').fill(message);
  await page.getByRole('button', { name: '후원하기' }).click();
  await expect(page).toHaveURL(/test-checkout\.html/);
  await expect(page.locator('#amount')).toHaveText('5,000원');
  await page.getByRole('button', { name: '결제하기' }).click();
  await expect(page).toHaveURL(/donate-result\.html\?.*paymentKey=/);
  await expect(page.locator('#headline')).toHaveText('5,000원 후원 완료');
  expect((await summary()).total).toBe(before + 5000);

  // 결과 페이지 새로고침 = 같은 승인 요청 반복 → 같은 결과, 적립은 그대로
  await page.reload();
  await expect(page.locator('#headline')).toHaveText('5,000원 후원 완료');
  expect((await summary()).total).toBe(before + 5000);

  // 방송 화면에 합계·메시지
  await page.getByRole('link', { name: '방송으로 돌아가기' }).click();
  await expect(page.locator('#donation-list')).toContainText(message);
  await expect(page.locator('#donation-total')).toHaveText(`${(before + 5000).toLocaleString('ko-KR')}원`);

  // 카드 거절 → 실패, 적립 안 됨
  await page.getByRole('button', { name: '1,000원' }).click();
  await page.getByRole('button', { name: '후원하기' }).click();
  await page.locator('input[name=card]').fill('4000 0000 0000 0002');
  await page.getByRole('button', { name: '결제하기' }).click();
  await expect(page.locator('#headline')).toHaveText('후원 실패');
  await expect(page.locator('#detail')).toContainText('승인되지 않았습니다');

  // 결제창 주소의 금액을 1,000,000 → 1,000으로 조작해 결제 → 서버가 주문 금액과 대조해 거부
  await page.goto(`${BASE}/broadcast.html?id=1`);
  await page.locator('#donation-form input[name=amount]').fill('1000000');
  await page.getByRole('button', { name: '후원하기' }).click();
  await expect(page).toHaveURL(/test-checkout\.html/);
  const tampered = new URL(page.url()); tampered.searchParams.set('amount', '1000');
  await page.goto(tampered.href);
  await page.getByRole('button', { name: '결제하기' }).click();
  await expect(page.locator('#headline')).toHaveText('후원 실패');
  await expect(page.locator('#detail')).toContainText('주문 금액과 다름');
  expect((await summary()).total).toBe(before + 5000);

  // 방송 소유자(demo1)에게는 후원 버튼 없음
  await page.getByRole('button', { name: '로그아웃' }).click();
  await login(page, 'demo1', 'demo1234');
  await page.goto(`${BASE}/broadcast.html?id=1`);
  await expect(page.locator('#donation-total')).toBeVisible();
  await expect(page.locator('#donation-form')).toBeHidden();
});
