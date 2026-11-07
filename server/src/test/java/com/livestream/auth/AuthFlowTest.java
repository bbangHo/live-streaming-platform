package com.livestream.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import com.livestream.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class AuthFlowTest extends ApiTestSupport {
    @Autowired
    UserRepository users;

    @Test
    void 가입_로그인_내정보_재발급_로그아웃() throws Exception {
        String name = uniqueName();
        String cred = "{\"username\":\"" + name + "\",\"password\":\"password123\"}";

        assertThat(postJson("/api/auth/signup", cred).getResponse().getStatus()).isEqualTo(201);
        // 비밀번호는 BCrypt 해시로만 저장된다
        String hash = users.findByUsername(name).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2").doesNotContain("password123");

        String login = postJson("/api/auth/login", cred).getResponse().getContentAsString();
        String access = JsonPath.read(login, "$.accessToken");
        String refresh = JsonPath.read(login, "$.refreshToken");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name));

        // 재발급: 새 쌍을 받고, 쓴 refresh token은 다시 못 쓴다(회전)
        var refreshed = postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}").getResponse();
        assertThat(refreshed.getStatus()).isEqualTo(200);
        String refresh2 = JsonPath.read(refreshed.getContentAsString(), "$.refreshToken");
        assertThat(postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}").getResponse().getStatus())
                .isEqualTo(401);

        // 로그아웃하면 그 refresh token으로 재발급 불가
        assertThat(postJson("/api/auth/logout", "{\"refreshToken\":\"" + refresh2 + "\"}").getResponse().getStatus())
                .isEqualTo(204);
        assertThat(postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh2 + "\"}").getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void README의_테스트_계정으로_로그인된다() throws Exception {
        for (String name : new String[] {"demo1", "demo2"}) {
            assertThat(postJson("/api/auth/login", "{\"username\":\"" + name + "\",\"password\":\"demo1234\"}")
                    .getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void 중복_아이디는_409() throws Exception {
        String cred = "{\"username\":\"" + uniqueName() + "\",\"password\":\"password123\"}";
        postJson("/api/auth/signup", cred);
        assertThat(postJson("/api/auth/signup", cred).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void 잘못된_가입_입력은_400() throws Exception {
        assertThat(postJson("/api/auth/signup", "{\"username\":\"a\",\"password\":\"short\"}").getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void 틀린_비밀번호와_없는_아이디는_똑같이_401() throws Exception {
        String name = uniqueName();
        postJson("/api/auth/signup", "{\"username\":\"" + name + "\",\"password\":\"password123\"}");
        assertThat(postJson("/api/auth/login", "{\"username\":\"" + name + "\",\"password\":\"wrongpass1\"}")
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(postJson("/api/auth/login", "{\"username\":\"nobody_" + name + "\",\"password\":\"password123\"}")
                .getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void 토큰_없거나_위조되면_내정보_401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized());
    }
}
