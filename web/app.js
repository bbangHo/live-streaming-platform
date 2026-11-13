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

/** 1234567(ms) → "20:34", 3600000 이상이면 "1:00:00" */
function formatDuration(ms) {
  const t = Math.round(ms / 1000), h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), sec = String(t % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

const STATUS_LABEL = { READY: '대기', LIVE: '방송 중', ENDED: '종료' };
const statusBadge = (s) => el('span', { className: `badge ${s.toLowerCase()}` }, STATUS_LABEL[s] ?? s);

/** 상단 메뉴를 그리고 현재 사용자를 돌려준다. */
async function renderNav() {
  const me = await currentUser();
  const nav = document.getElementById('nav');
  const next = encodeURIComponent(location.pathname.replace(/^\//, '') + location.search);
  nav.replaceChildren(...el('div', {},
    el('a', { href: 'index.html', className: 'logo' }, '● LIVE'),
    el('a', { href: 'index.html' }, '방송'),
    el('a', { href: 'recordings.html' }, '다시보기'),
    el('span', { className: 'spacer' }),
    me ? el('span', { className: 'me' }, me.username) : null,
    me ? el('button', { onclick: logout, className: 'link' }, '로그아웃')
       : el('a', { href: `login.html?next=${next}` }, '로그인'),
  ).childNodes); // el()이 null을 걸러 준다
  return me;
}
