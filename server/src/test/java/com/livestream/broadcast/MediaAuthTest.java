package com.livestream.broadcast;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

/** MediaMTX 송출 인가: 소유자 토큰 + 끝나지 않은 방송만 publish 허용, 시청은 공개. */
class MediaAuthTest extends ApiTestSupport {
    String owner;
    String other;
    long id;
    String key;

    @BeforeEach
    void setUp() throws Exception {
        owner = accessTokenOfNewUser();
        other = accessTokenOfNewUser();
        String created = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"송출 인가\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        id = ((Number) JsonPath.read(created, "$.id")).longValue();
        key = JsonPath.read(created, "$.streamKey");
    }

    private void expectAuth(String action, String path, String token, ResultMatcher result) throws Exception {
        String body = "{\"action\":\"" + action + "\",\"path\":\"" + path + "\",\"protocol\":\"webrtc\",\"token\":"
                + (token == null ? "\"\"" : "\"" + token + "\"") + "}";
        mvc.perform(post("/internal/media-auth").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(result);
    }

    @Test
    void 소유자는_대기_방송중에_송출_가능하고_종료후엔_불가() throws Exception {
        expectAuth("publish", "live/" + key, owner, status().isOk());
        mvc.perform(post("/api/broadcasts/" + id + "/start").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        expectAuth("publish", "live/" + key, owner, status().isOk());
        mvc.perform(post("/api/broadcasts/" + id + "/stop").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        expectAuth("publish", "live/" + key, owner, status().isUnauthorized());
    }

    @Test
    void 타인_비로그인_잘못된토큰_없는키는_송출_401() throws Exception {
        expectAuth("publish", "live/" + key, other, status().isUnauthorized());
        expectAuth("publish", "live/" + key, null, status().isUnauthorized());
        expectAuth("publish", "live/" + key, "not-a-jwt", status().isUnauthorized());
        expectAuth("publish", "live/nosuchkey", owner, status().isUnauthorized());
        expectAuth("publish", "other/" + key, owner, status().isUnauthorized());
    }

    @Test
    void 시청은_누구나() throws Exception {
        expectAuth("read", "live/" + key, null, status().isOk());
    }
}
