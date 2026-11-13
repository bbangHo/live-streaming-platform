package com.livestream.broadcast;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 인가 3종: 비로그인(401) / 소유자(허용) / 타인(403). */
class BroadcastAuthorizationTest extends ApiTestSupport {
    String owner;
    String other;
    long id;

    @BeforeEach
    void setUp() throws Exception {
        owner = accessTokenOfNewUser();
        other = accessTokenOfNewUser();
        String created = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"첫 방송\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        id = ((Number) JsonPath.read(created, "$.id")).longValue();
    }

    private static MockHttpServletRequestBuilder rename(long id) {
        return patch("/api/broadcasts/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"바꾼 제목\"}");
    }

    @Test
    void 비로그인도_목록_상세_조회는_된다() throws Exception {
        mvc.perform(get("/api/broadcasts")).andExpect(status().isOk());
        mvc.perform(get("/api/broadcasts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("첫 방송"));
    }

    @Test
    void 비로그인은_등록_수정_삭제_송출제어_모두_401() throws Exception {
        mvc.perform(post("/api/broadcasts").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(rename(id)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/broadcasts/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/broadcasts/" + id + "/stop")).andExpect(status().isUnauthorized());
    }

    @Test
    void 타인은_수정_삭제_송출제어_모두_403이고_데이터는_그대로() throws Exception {
        goLive(id);
        mvc.perform(rename(id).header(HttpHeaders.AUTHORIZATION, bearer(other))).andExpect(status().isForbidden());
        mvc.perform(post("/api/broadcasts/" + id + "/stop").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/broadcasts/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/broadcasts/" + id))
                .andExpect(jsonPath("$.title").value("첫 방송"))
                .andExpect(jsonPath("$.status").value("LIVE"));
    }

    @Test
    void 소유자는_수정_종료_삭제_가능() throws Exception {
        String auth = bearer(owner);
        mvc.perform(rename(id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("바꾼 제목"));
        goLive(id);
        mvc.perform(post("/api/broadcasts/" + id + "/stop").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENDED"));
        mvc.perform(delete("/api/broadcasts/" + id).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/broadcasts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void 없는_방송은_404() throws Exception {
        mvc.perform(rename(999_999_999L).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }
}
