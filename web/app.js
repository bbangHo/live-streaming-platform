// 공용: 토큰 보관, API 호출(만료 시 refresh 1회 재시도), 상단 메뉴, DOM 헬퍼
// shortcut: 토큰을 localStorage에 둔다(XSS에 노출). 사용자 입력은 전부 textContent로만 그려 XSS 경로를 막는다. 필요하면 httpOnly 쿠키로 바꾼다

const TOKEN_KEY = 'ls.tokens';

const tokens = {
  get() { try { return JSON.parse(localStorage.getItem(TOKEN_KEY)); } catch { return null; } },
  set(t) { t ? localStorage.setItem(TOKEN_KEY, JSON.stringify(t)) : localStorage.removeItem(TOKEN_KEY); },
};

let refreshing = null; // 동시에 여러 요청이 401을 받아도 refresh는 한 번만 (refresh token은 1회용)

async function refreshTokens() {
  const t = tokens.get();
  if (!t?.refreshToken) return false;
  refreshing ??= fetch('/api/auth/refresh', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: t.refreshToken }),
  }).then(async (res) => {
    tokens.set(res.ok ? await res.json() : null);
    return res.ok;
  }).finally(() => { refreshing = null; });
  return refreshing;
}

/** API 호출. 실패하면 Error(status, ProblemDetail의 detail)를 던진다. */
async function api(path, { method = 'GET', body } = {}, retried = false) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const t = tokens.get();
  if (t?.accessToken) headers.Authorization = `Bearer ${t.accessToken}`;

  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.status === 401 && t?.accessToken && !retried && await refreshTokens()) {
    return api(path, { method, body }, true);
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    const err = new Error(problem.detail || `요청 실패 (${res.status})`);
    err.status = res.status;
    err.body = problem;
    throw err;
  }
  return res.status === 204 ? null : res.json();
}

/** 로그인 상태면 {id, username}, 아니면 null. */
async function currentUser() {
  if (!tokens.get()) return null;
  try { return await api('/api/auth/me'); } catch { tokens.set(null); return null; }
}

async function logout() {
  const t = tokens.get();
  tokens.set(null); // 서버 폐기 요청이 끊겨도 이 브라우저에서는 먼저 로그아웃
  if (t?.refreshToken) await api('/api/auth/logout', { method: 'POST', body: { refreshToken: t.refreshToken } }).catch(() => {});
  location.href = 'index.html';
}

/** el('a', {href: '#', className: 'x'}, '텍스트', child) — 문자열은 텍스트 노드로만 들어간다. */
function el(tag, props = {}, ...children) {
  const node = Object.assign(document.createElement(tag), props);
  node.append(...children.filter((c) => c !== null && c !== undefined && c !== false));
  return node;
}

// 시간: 서버는 UTC(ISO)로 주고, 화면은 항상 KST로 보여 준다 (보는 사람의 PC 시간대와 무관)
const KST = { timeZone: 'Asia/Seoul' };
const kstDateTime = (iso) => new Date(iso).toLocaleString('ko-KR', KST);
const kstTime = (iso) => new Date(iso).toLocaleTimeString('ko-KR', KST);

/** 1234567(ms) → "20:34", 3600000 이상이면 "1:00:00" */
function formatDuration(ms) {
  const t = Math.round(ms / 1000), h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), sec = String(t % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

const STATUS_LABEL = { READY: '대기', LIVE: '방송 중', ENDED: '종료' };
const statusBadge = (s) => el('span', { className: `badge ${s.toLowerCase()}` }, STATUS_LABEL[s] ?? s);

const CATEGORY_LABEL = { GAME: '게임', TALK: '토크', MUSIC: '음악', DEV: '개발', SPORTS: '스포츠', ETC: '기타' };

/** 이름 첫 글자 동그라미. 색은 이름으로 정해져 같은 사람은 늘 같은 색 */
function avatar(name, size = '') {
  const n = name ?? '?';
  let h = 0; for (const ch of n) h = (h * 31 + ch.codePointAt(0)) % 360;
  const a = el('span', { className: `avatar ${size}`.trim() }, [...n][0].toUpperCase());
  a.style.background = `hsl(${(h * 97) % 360} 55% 40%)`;
  return a;
}

/** 시청자 수 표시. 서버가 아직 viewerCount를 안 주면 null */
const viewers = (b) => (typeof b.viewerCount === 'number' ? `${b.viewerCount.toLocaleString('ko-KR')}명 시청` : null);

/** 방송 카드: 썸네일 자리 + 좌상단 상태·시청자 수, 아래에 방송자 동그라미·제목·이름·카테고리 */
function broadcastCard(b) {
  return el('li', { className: 'card' },
    el('a', { href: `broadcast.html?id=${b.id}` },
      el('span', { className: 'thumb-tags' }, statusBadge(b.status), b.status === 'LIVE' && viewers(b) ? el('span', { className: 'badge' }, viewers(b)) : null),
      el('div', { className: 'card-body' }, avatar(b.ownerUsername),
        el('div', { className: 'card-text' },
          el('h3', {}, b.title),
          el('div', { className: 'muted' }, `${b.ownerUsername ?? '알 수 없음'} · ${kstDateTime(b.createdAt)}`),
          CATEGORY_LABEL[b.category] ? el('span', { className: 'tag' }, CATEGORY_LABEL[b.category]) : null))));
}

/** 다시보기 카드 */
function vodCard(r) {
  return el('li', { className: 'card' },
    el('a', { href: `recording.html?id=${r.id}` },
      el('span', { className: 'thumb-tags' }, el('span', { className: 'badge' }, `▶ ${formatDuration(r.durationMs)}`)),
      el('div', { className: 'card-body' }, avatar(r.ownerUsername),
        el('div', { className: 'card-text' },
          el('h3', {}, r.broadcastTitle ?? '(삭제된 방송)'),
          el('div', { className: 'muted' }, `${r.ownerUsername ?? '알 수 없음'} · ${kstDateTime(r.startedAt)}`)))));
}

/** 상단 메뉴를 그리고 현재 사용자를 돌려준다. 넓은 화면 페이지(main.wide)에는 왼쪽 채널 사이드바도 그린다. */
async function renderNav() {
  const me = await currentUser();
  const nav = document.getElementById('nav');
  const next = encodeURIComponent(location.pathname.replace(/^\//, '') + location.search);
  const q = new URLSearchParams(location.search).get('q') ?? '';
  const search = el('form', { className: 'search', action: 'index.html', role: 'search' },
    el('input', { name: 'q', type: 'search', placeholder: '방송·채널 검색', value: q, autocomplete: 'off' }),
    el('button', { className: 'search-btn', ariaLabel: '검색' }, '⌕'));
  const menu = me ? el('details', { className: 'menu' },
    el('summary', { className: 'me' }, avatar(me.username, 'sm'), el('span', {}, me.username)),
    el('div', { className: 'menu-panel' },
      el('a', { href: `channel.html?id=${me.id}` }, '내 채널'),
      el('a', { href: 'index.html#create' }, '방송 만들기'),
      el('a', { href: 'recordings.html' }, '다시보기'),
      el('button', { onclick: logout, className: 'link' }, '로그아웃'))) : null;
  nav.replaceChildren(...el('div', {},
    el('a', { href: 'index.html', className: 'logo' }, '● LIVE'),
    el('a', { href: 'index.html', className: 'nav-link' }, '방송'),
    el('a', { href: 'recordings.html', className: 'nav-link' }, '다시보기'),
    el('span', { className: 'spacer' }),
    search,
    el('span', { className: 'spacer' }),
    me ? el('a', { href: 'index.html#create', className: 'button primary go-live' }, '방송하기') : null,
    menu ?? el('a', { href: `login.html?next=${next}`, className: 'button primary' }, '로그인'),
  ).childNodes); // el()이 null을 걸러 준다
  document.addEventListener('click', (e) => { if (menu && !menu.contains(e.target)) menu.open = false; });
  if (document.querySelector('main.wide')) renderSide(me);
  return me;
}

/** 왼쪽 사이드바: 팔로잉 채널(로그인 시, 서버가 지원할 때) + 지금 방송 중인 채널 */
async function renderSide(me) {
  let side = document.getElementById('side');
  if (!side) {
    side = el('aside', { id: 'side' });
    document.getElementById('nav').after(side);
    document.body.classList.add('has-side');
  }
  const row = (href, name, sub, live) => el('a', { href, className: 'side-row' }, avatar(name),
    el('span', { className: 'side-text' }, el('b', {}, name), sub ? el('small', {}, sub) : null),
    live ? el('span', { className: 'dot', title: '방송 중' }) : el('small', { className: 'off' }, '오프라인'));
  const [following, lives] = await Promise.all([
    me ? api('/api/me/following').catch(() => null) : null,
    api('/api/broadcasts?status=LIVE').catch(() => []),
  ]);
  side.replaceChildren(...[
    following ? el('h4', {}, '팔로잉 채널') : null,
    ...(following ?? []).map((c) => row(c.liveBroadcastId ? `broadcast.html?id=${c.liveBroadcastId}` : `channel.html?id=${c.id}`,
      c.username, c.liveTitle, !!c.liveBroadcastId)),
    following && following.length === 0 ? el('p', { className: 'muted side-empty' }, '팔로우한 채널이 없어요') : null,
    el('h4', {}, '지금 방송 중'),
    ...lives.slice(0, 10).map((b) => row(`broadcast.html?id=${b.id}`, b.ownerUsername ?? '알 수 없음',
      [CATEGORY_LABEL[b.category], b.title].filter(Boolean).join(' · '), true)),
    lives.length === 0 ? el('p', { className: 'muted side-empty' }, '방송 중인 채널이 없어요') : null,
  ].filter(Boolean));
}
