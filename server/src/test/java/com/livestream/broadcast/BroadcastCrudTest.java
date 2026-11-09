package com.livestream.broadcast;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 방송 CRUD·상태 전이 (인가는 BroadcastAuthorizationTest). */
class BroadcastCrudTest extends ApiTestSupport {
    String auth;

    @BeforeEach
    void setUp() throws Exception {
        auth = bearer(accessTokenOfNewUser());
    }

    private long create(String json) throws Exception {
        String body = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions act(long id, String action) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/broadcasts/" + id + "/" + action).header(HttpHeaders.AUTHORIZATION, auth));
    }

    @Test
    void 등록하면_스트림키와_HLS주소와_방송자가_붙는다() throws Exception {
        long id = create("{\"title\":\"t1\",\"description\":\"설명\"}");
        mvc.perform(get("/api/broadcasts/" + id))
                .andExpect(jsonPath("$.title").value("t1"))
                .andExpect(jsonPath("$.description").value("설명"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.ownerUsername").isString())
                .andExpect(jsonPath("$.streamKey", matchesPattern("[0-9a-f]{16}")))
                .andExpect(jsonPath("$.hlsPath", matchesPattern("/live/[0-9a-f]{16}/index\\.m3u8")));
    }

    @Test
    void 수정하면_제목과_설명이_바뀐다() throws Exception {
        long id = create("{\"title\":\"before\"}");
        mvc.perform(patch("/api/broadcasts/" + id).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"after\",\"description\":\"d\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/broadcasts/" + id))
                .andExpect(jsonPath("$.title").value("after"))
                .andExpect(jsonPath("$.description").value("d"));
    }

    @Test
    void 목록은_상태로_거를_수_있다() throws Exception {
        long ready = create("{\"title\":\"ready\"}");
        long live = create("{\"title\":\"live\"}");
        act(live, "start").andExpect(status().isOk());

        mvc.perform(get("/api/broadcasts?status=LIVE"))
                .andExpect(jsonPath("$[*].id", hasItem((int) live)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) ready))));
        mvc.perform(get("/api/broadcasts")).andExpect(jsonPath("$[*].id", hasItem((int) ready)));
        mvc.perform(get("/api/broadcasts?status=NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void 상태는_대기_방송중_종료_순서로만_바뀐다() throws Exception {
        long id = create("{\"title\":\"flow\"}");
        act(id, "stop").andExpect(status().isConflict());          // 대기 → 종료 불가
        act(id, "start").andExpect(jsonPath("$.status").value("LIVE"));
        act(id, "start").andExpect(status().isConflict());         // 이미 방송 중
        act(id, "stop").andExpect(jsonPath("$.status").value("ENDED"));
        act(id, "start").andExpect(status().isConflict());         // 종료된 방송 재시작 불가
        act(id, "stop").andExpect(status().isConflict());
    }

    @Test
    void 빈_제목이나_너무_긴_입력은_400() throws Exception {
        mvc.perform(post("/api/broadcasts")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/broadcasts")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
