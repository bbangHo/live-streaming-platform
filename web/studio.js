// 송출 페이지 공통 (publish-pc.html, publish-mobile.html).
// 페이지는 capture()만 다르게 넘긴다: PC = 화면+탭 소리+마이크, 모바일 = 카메라+마이크.
//
// 방송 상태는 서버가 정한다: 송출이 MediaMTX에 들어오면 서버가 '방송 중'으로, 끊긴 채 30초가 지나면 '종료'로 바꾼다.
// 이 페이지는 (1) WHIP 송출을 열고 닫고 (2) '방송 종료' 버튼으로 종료 API를 부르고 (3) 서버 상태를 2초마다 보여 준다.

/**
 * @param {object} opts
 * @param {(log: (m: string) => void) => Promise<{stream: MediaStream, stop: () => void}>} opts.capture
 *        송출할 미디어를 얻는다. stop()은 원본 장치(화면·마이크·카메라)와 오디오 믹서까지 모두 끈다
 * @param {(ctx: {media: MediaStream, pc: RTCPeerConnection}) => void} [opts.onLive]  송출이 열린 직후 (카메라 전환 등)
 * @param {() => void} [opts.onStop]  송출을 멈춘 직후
 */
function initStudio({ capture, onLive = () => {}, onStop = () => {} }) {
  const $ = (id) => document.getElementById(id);
  const id = new URLSearchParams(location.search).get('id');
  let broadcast = null, session = null, media = null, stopCapture = null;

  const log = (m) => { $('log').textContent = `${new Date().toLocaleTimeString()} ${m}\n` + $('log').textContent; };
  const setConn = (text) => { $('conn').textContent = text; };

  function setButtons() {
    const ended = !broadcast || broadcast.status === 'ENDED';
    $('start').disabled = ended || !!session;
    $('end').disabled = ended || (broadcast.status !== 'LIVE' && !session);
  }

  function renderBroadcast(b) {
    broadcast = b;
    $('title').textContent = b.title;
    document.title = `송출 · ${b.title}`;
    $('status').replaceChildren(statusBadge(b.status));
    $('watch-link').href = `broadcast.html?id=${b.id}`;
    if (b.status === 'ENDED') {
      $('hint').textContent = '종료된 방송입니다. 다시 방송하려면 새 방송을 만드세요.';
      if (session) stopSending('방송이 종료되어 송출을 멈춤');
    } else if (b.status === 'LIVE' && !session) {
      $('hint').textContent = '방송 중이지만 이 페이지에서 송출하고 있지 않습니다. 30초 안에 다시 시작하지 않으면 방송이 종료됩니다.';
    } else if (b.status === 'LIVE') {
      $('hint').textContent = '방송 중 — 시청자에게 1~4초 늦게 보입니다.';
    } else {
      $('hint').textContent = session ? '송출 연결됨 — 곧 방송 중으로 바뀝니다.' : '「방송 시작」을 누르면 송출이 시작됩니다.';
    }
    setButtons();
  }

  async function refresh() {
    try { renderBroadcast(await api(`/api/broadcasts/${encodeURIComponent(id)}`)); }
    catch (err) { $('error').textContent = err.status === 404 ? '없는 방송입니다.' : err.message; }
  }

  /** 송출만 멈춘다(방송은 유예 시간 동안 '방송 중' 유지). */
  function stopSending(reason) {
    session?.close(); session = null;
    stopCapture?.(); stopCapture = null;
    media = null;
    onStop();
    $('preview').srcObject = null;
    setConn('송출 안 함');
    if (reason) log(reason);
    setButtons();
  }

  async function start() {
    $('error').textContent = '';
    $('start').disabled = true;
    try {
      // access token이 만료됐으면 여기서 refresh된다 (WHIP 요청에는 refresh 재시도가 없으므로 먼저)
      await api('/api/auth/me');
      const captured = await capture(log);
      media = captured.stream;
      stopCapture = captured.stop;
      $('preview').srcObject = media;
      // 브라우저의 「공유 중지」나 카메라 끊김 → 송출만 멈추고, 방송 종료 여부는 사용자가 고른다
      media.getVideoTracks()[0]?.addEventListener('ended', () => {
        if (!session) return;
        stopSending('브라우저에서 캡처가 중지됨');
        $('hint').textContent = '캡처가 중지됐습니다. 30초 안에 「방송 시작」을 다시 누르면 방송이 이어지고, 아니면 자동으로 종료됩니다.';
      });

      setConn('연결 중…');
      session = await publishWhip(media, {
        url: `${location.origin}/live/${encodeURIComponent(broadcast.streamKey)}/whip`,
        token: tokens.get()?.accessToken,
        onState: (s) => {
          setConn({ connecting: '연결 중…', connected: '송출 중', disconnected: '연결 불안정…', failed: '연결 실패', closed: '송출 안 함' }[s] ?? s);
          log('연결 상태: ' + s);
          if (s === 'failed' && session) stopSending('WebRTC 연결 실패 — 네트워크(UDP 8189) 확인 후 다시 시작하세요');
        },
      });
      log('송출 시작 (WHIP 201)');
      onLive({ media, pc: session.pc });
      setButtons();
      refresh();
    } catch (err) {
      $('error').textContent = err.message;
      log('시작 실패: ' + err.message);
      stopSending();
    }
  }

  async function end() {
    if (!confirm('방송을 종료할까요? 종료한 방송은 다시 시작할 수 없습니다.')) return;
    stopSending('방송 종료');
    try {
      if (broadcast.status === 'LIVE') renderBroadcast(await api(`/api/broadcasts/${id}/stop`, { method: 'POST' }));
      else refresh(); // 아직 서버가 방송 중으로 바꾸기 전에 끝냈으면 대기 상태로 남는다
    } catch (err) { $('error').textContent = err.message; }
  }

  $('start').onclick = start;
  $('end').onclick = end;
  // 송출 중에 페이지를 떠나려 하면 확인. 떠나면 WHIP 세션을 닫고, 방송은 30초 뒤 자동 종료(그 안에 다시 오면 이어짐)
  addEventListener('beforeunload', (e) => { if (session) e.preventDefault(); });
  addEventListener('pagehide', () => session?.close());

  renderNav().then(async (me) => {
    if (!me) { location.href = `login.html?next=${encodeURIComponent(location.pathname.replace(/^\//, '') + location.search)}`; return; }
    if (!id) { $('error').textContent = '방송 id가 없습니다. 방송 상세 페이지의 송출 링크로 들어오세요.'; return; }
    await refresh();
    if (broadcast && broadcast.ownerId !== me.id) {
      $('error').textContent = '이 방송의 소유자만 송출할 수 있습니다.';
      broadcast = null; setButtons(); // 버튼을 막아도 서버(MediaMTX → 백엔드)가 다시 거부한다
      return;
    }
    setInterval(refresh, 2000);
  });
}
