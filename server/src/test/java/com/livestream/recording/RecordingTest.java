package com.livestream.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 녹화: MediaMTX 완료 알림 → 위치 영구 저장(멱등) → 공개 목록·상세(재생 주소) → 방송 소유자만 삭제. */
class RecordingTest extends ApiTestSupport {
    String owner;
    String other;
    long broadcastId;
    String key;

    @BeforeEach
    void setUp() throws Exception {
        owner = accessTokenOfNewUser();
        other = accessTokenOfNewUser();
        String body = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"녹화 방송\"}"))
                .andReturn().getResponse().getContentAsString();
        broadcastId = ((Number) JsonPath.read(body, "$.id")).longValue();
        key = JsonPath.read(body, "$.streamKey");
    }

    private ResultActions hook(String secret, String path, String file, double seconds) throws Exception {
        return mvc.perform(post("/api/internal/media/recordings?secret=" + secret).contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"%s\",\"segmentPath\":\"%s\",\"durationSeconds\":%s}".formatted(path, file, seconds)));
    }

    private String file(String time) {
        return "/recordings/live/" + key + "/" + time + ".mp4";
    }

    @Test
    void 파일_이름에서_시작_시각을_읽는다() {
        assertThat(RecordingService.startOf("/recordings/live/k/2026-11-13_10-53-11-123456Z.mp4"))
                .contains(Instant.parse("2026-11-13T10:53:11.123456Z"));
        assertThat(RecordingService.startOf("/r/live/k/2026-11-13_19-53-11-000001+0900.mp4"))
                .contains(Instant.parse("2026-11-13T10:53:11.000001Z"));
        assertThat(RecordingService.startOf("/r/live/k/weird.mp4")).isEmpty();
    }

    @Test
    void 완료_알림이_오면_위치가_저장되고_누구나_목록_상세를_본다() throws Exception {
        hook(HOOK_SECRET, "live/" + key, file("2026-11-13_10-53-11-123456Z"), 12.5).andExpect(status().isNoContent());
        hook(HOOK_SECRET, "live/" + key, file("2026-11-13_10-53-11-123456Z"), 12.5).andExpect(status().isNoContent()); // 같은 알림 두 번
        hook(HOOK_SECRET, "live/" + key, file("2026-11-13_11-00-00-000000Z"), 3);

        String list = mvc.perform(get("/api/broadcasts/" + broadcastId + "/recordings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))               // 멱등: 2개만
                .andExpect(jsonPath("$[0].filePath").value(file("2026-11-13_10-53-11-123456Z")))
                .andExpect(jsonPath("$[0].startedAt").value("2026-11-13T10:53:11.123456Z"))
                .andExpect(jsonPath("$[0].durationMs").value(12500))
                .andExpect(jsonPath("$[0].broadcastTitle").value("녹화 방송"))
                .andExpect(jsonPath("$[0].playbackPath", startsWith("/playback/get?path=live%2F" + key + "&start=2026-11-13T10%3A53%3A11.123456Z&duration=14&format=mp4")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(list, "$[0].id")).longValue();
        mvc.perform(get("/api/recordings/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").isNumber());
        mvc.perform(get("/api/recordings")).andExpect(status().isOk());
    }

    @Test
    void 등록_안_된_키는_무시하고_비밀값이_틀리면_403() throws Exception {
        hook(HOOK_SECRET, "live/not-registered", "/recordings/live/not-registered/x.mp4", 1).andExpect(status().isNoContent());
        hook("wrong", "live/" + key, file("2026-11-13_10-53-11-123456Z"), 1).andExpect(status().isForbidden());
        hook(HOOK_SECRET, "other/" + key, file("2026-11-13_10-53-11-123456Z"), 1).andExpect(status().isBadRequest());
        mvc.perform(get("/api/broadcasts/" + broadcastId + "/recordings")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 삭제는_방송_소유자만() throws Exception {
        hook(HOOK_SECRET, "live/" + key, file("2026-11-13_10-53-11-123456Z"), 5);
        long id = ((Number) JsonPath.read(mvc.perform(get("/api/broadcasts/" + broadcastId + "/recordings"))
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
        mvc.perform(delete("/api/recordings/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/recordings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other))).andExpect(status().isForbidden());
        mvc.perform(get("/api/recordings/" + id)).andExpect(status().isOk());
        // 소유자: MediaMTX가 없어 파일 삭제는 실패해도(로그만) 목록에서는 지운다
        mvc.perform(delete("/api/recordings/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/recordings/" + id)).andExpect(status().isNotFound());
    }
}
