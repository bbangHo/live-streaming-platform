package com.livestream.stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** MediaMTX → 백엔드 송출 인증(authHTTPAddress). 송출은 방송 소유자의 로그인 토큰이 있어야만 된다. */
class StreamAuthTest extends ApiTestSupport {
    String owner;
    String other;
    long id;
    String key;

    @BeforeEach
    void setUp() throws Exception {
        owner = accessTokenOfNewUser();
        other = accessTokenOfNewUser();
        String body = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"송출 인증\"}"))
                .andReturn().getResponse().getContentAsString();
        id = ((Number) JsonPath.read(body, "$.id")).longValue();
        key = JsonPath.read(body, "$.streamKey");
    }

    private ResultActions ask(String secret, String action, String path, String token) throws Exception {
        String json = """
                {"ip":"172.18.0.1","user":"","password":"","token":"%s","action":"%s","path":"%s",
                 "protocol":"webrtc","id":null,"query":"","userAgent":"test"}""".formatted(token, action, path);
        return mvc.perform(post("/api/internal/media/auth?secret=" + secret)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 소유자_토큰이면_송출_허용() throws Exception {
        ask(HOOK_SECRET, "publish", "live/" + key, owner).andExpect(status().isOk());
    }

    @Test
    void 토큰_없음_위조_타인이면_송출_거부() throws Exception {
        ask(HOOK_SECRET, "publish", "live/" + key, "").andExpect(status().isUnauthorized());
        ask(HOOK_SECRET, "publish", "live/" + key, "not-a-jwt").andExpect(status().isUnauthorized());
        ask(HOOK_SECRET, "publish", "live/" + key, other).andExpect(status().isUnauthorized());
    }

    @Test
    void 시청은_누구나_허용() throws Exception {
        ask(HOOK_SECRET, "read", "live/" + key, "").andExpect(status().isOk());
    }

    @Test
    void 종료된_방송에는_소유자도_다시_송출할_수_없다() throws Exception {
        syncWithOnline(Instant.now(), key);
        mvc.perform(post("/api/broadcasts/" + id + "/stop").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        ask(HOOK_SECRET, "publish", "live/" + key, owner).andExpect(status().isUnauthorized());
    }

    @Test
    void 방송_중에_끊겼다가_다시_붙는_송출은_허용() throws Exception {
        syncWithOnline(Instant.now(), key);
        ask(HOOK_SECRET, "publish", "live/" + key, owner).andExpect(status().isOk());
    }

    @Test
    void 등록되지_않은_키와_이상한_경로는_거부_devBypass_꺼짐() throws Exception {
        ask(HOOK_SECRET, "publish", "live/test", owner).andExpect(status().isUnauthorized());
        ask(HOOK_SECRET, "publish", "other/" + key, owner).andExpect(status().isUnauthorized());
        ask(HOOK_SECRET, "api", "", owner).andExpect(status().isUnauthorized());
    }

    @Test
    void hook_secret이_틀리면_403() throws Exception {
        ask("wrong", "publish", "live/" + key, owner).andExpect(status().isForbidden());
        ask("", "read", "live/" + key, "").andExpect(status().isForbidden());
    }
}
