package com.livestream.stream;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** MediaMTX Control API(v3) 중 쓰는 것만: 송출 중인 경로 목록, WebRTC 세션 끊기, 녹화 파일 삭제. */
@Component
public class MediaMtxClient {
    private final RestClient http;

    public MediaMtxClient(MediaProperties props) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().baseUrl(props.apiUrl()).requestFactory(timeouts).build();
    }

    /** 지금 송출이 들어오고 있는 경로. sourceType은 예: "webRTCSession", sourceId는 그 세션 id. */
    public record OnlinePath(String name, String sourceType, String sourceId) {}

    record PathList(Integer pageCount, List<Map<String, Object>> items) {}

    @SuppressWarnings("unchecked")
    public List<OnlinePath> onlinePaths() {
        PathList list = http.get().uri("/v3/paths/list?itemsPerPage=1000").retrieve().body(PathList.class);
        List<OnlinePath> out = new ArrayList<>();
        if (list == null || list.items() == null) return out;
        for (Map<String, Object> item : list.items()) {
            // 1.21: online(신) / ready(구) — 둘 중 하나라도 true면 송출 중
            boolean online = Boolean.TRUE.equals(item.get("online")) || Boolean.TRUE.equals(item.get("ready"));
            if (!online) continue;
            Map<String, Object> source = (Map<String, Object>) item.get("source");
            out.add(new OnlinePath((String) item.get("name"),
                    source == null ? null : (String) source.get("type"),
                    source == null ? null : (String) source.get("id")));
        }
        return out;
    }

    /** 녹화 파일(세그먼트) 하나를 지운다. start = 세그먼트 시작 시각. */
    public void deleteRecordingSegment(String path, Instant start) {
        http.delete().uri(b -> b.path("/v3/recordings/segments/delete")
                        .queryParam("path", path).queryParam("start", start.toString()).build())
                .retrieve().toBodilessEntity();
    }

    /** 송출 세션을 끊는다. 종료된 방송에 아직 붙어 있는 송출 등. */
    public void kick(OnlinePath path) {
        if ("webRTCSession".equals(path.sourceType()) && path.sourceId() != null) {
            http.post().uri("/v3/webrtc/sessions/kick/{id}", path.sourceId()).retrieve().toBodilessEntity();
        }
    }
}
