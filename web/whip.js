// WHIP 송출 (PC·모바일 공통): MediaStream → WebRTC → MediaMTX. 주소는 같은 출처의 /live/<키>/whip (nginx·Caddy가 MediaMTX로 넘김)
// 표준: RFC 9725 (WHIP) — POST로 SDP offer를 보내고 201 + answer를 받는다. 종료는 Location(세션 주소)에 DELETE.

/** WHIP 실패. status: HTTP 상태(401 = 송출 권한 없음, 400 = 이미 송출 중 등), 네트워크 오류면 0. */
class WhipError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

/**
 * media를 송출하고 {pc, close()}를 돌려준다.
 * token: 로그인 access token. MediaMTX가 백엔드에 넘겨 "이 방송의 소유자인가"를 확인한다.
 * onState(connectionState): 'connecting' | 'connected' | 'disconnected' | 'failed' | 'closed'
 */
async function publishWhip(media, { url, token, onState = () => {} }) {
  const pc = new RTCPeerConnection();
  pc.onconnectionstatechange = () => onState(pc.connectionState);
  for (const track of media.getTracks()) {
    const tr = pc.addTransceiver(track, { direction: 'sendonly' });
    // HLS(fMP4)는 VP8을 못 담는다 → 영상은 H264 우선
    if (track.kind === 'video' && RTCRtpSender.getCapabilities) {
      const codecs = RTCRtpSender.getCapabilities('video').codecs;
      tr.setCodecPreferences([...codecs.filter((c) => c.mimeType === 'video/H264'), ...codecs.filter((c) => c.mimeType !== 'video/H264')]);
    }
  }

  let sessionUrl = null;
  try {
    await pc.setLocalDescription(await pc.createOffer());
    // shortcut: trickle ICE 없이 후보 수집이 끝난 뒤 한 번에 보낸다(로컬·공인 IP면 충분). 연결이 느리면 trickle(PATCH)로 바꾼다
    await new Promise((ok) => {
      if (pc.iceGatheringState === 'complete') return ok();
      pc.onicegatheringstatechange = () => pc.iceGatheringState === 'complete' && ok();
      setTimeout(ok, 3000);
    });

    const headers = { 'Content-Type': 'application/sdp' };
    if (token) headers.Authorization = `Bearer ${token}`;
    let res;
    try {
      res = await fetch(url, { method: 'POST', headers, body: pc.localDescription.sdp });
    } catch (e) {
      throw new WhipError(0, '미디어 서버에 연결할 수 없습니다: ' + e.message);
    }
    if (res.status !== 201) {
      const text = (await res.text()).trim();
      const reason = res.status === 401 ? '송출 권한이 없습니다 (방송 소유자로 로그인했는지, 종료된 방송이 아닌지 확인)'
        : /already publishing|someone is publishing/i.test(text) ? '이미 다른 곳에서 이 방송을 송출 중입니다'
        : `송출 실패 (WHIP ${res.status}) ${text}`;
      throw new WhipError(res.status, reason);
    }
    const loc = res.headers.get('Location');
    sessionUrl = loc && new URL(loc, url).href;
    await pc.setRemoteDescription({ type: 'answer', sdp: await res.text() });
  } catch (e) {
    pc.close();
    throw e;
  }

  return {
    pc,
    /** WHIP 세션 DELETE → MediaMTX가 바로 경로를 닫는다. 페이지를 닫을 때도 쓸 수 있게 keepalive */
    close() {
      if (sessionUrl) fetch(sessionUrl, { method: 'DELETE', keepalive: true }).catch(() => {});
      sessionUrl = null;
      pc.close();
    },
  };
}
