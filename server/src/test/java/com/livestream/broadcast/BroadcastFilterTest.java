package com.livestream.broadcast;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 카테고리(기본값·수정 유지·잘못된 값) + 목록 필터(category·q·ownerId·조합). 테스트끼리 DB를 같이 쓰므로 ownerId로 범위를 좁힌다. */
class BroadcastFilterTest extends ApiTestSupport {
    String auth;
    long ownerId;
    String ownerName;

    @BeforeEach
    void setUp() throws Exception {
        String login = signupAndLogin();
        auth = bearer(JsonPath.read(login, "$.accessToken"));
        String me = mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andReturn().getResponse().getContentAsString();
        ownerId = ((Number) JsonPath.read(me, "$.id")).longValue();
        ownerName = JsonPath.read(me, "$.username");
    }

    private long create(String json) throws Exception {
        String body = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 카테고리_기본_TALK_수정시_생략하면_유지_잘못된_값_400() throws Exception {
        long id = create("{\"title\":\"t\"}");
        mvc.perform(get("/api/broadcasts/" + id))
                .andExpect(jsonPath("$.category").value("TALK"))
                .andExpect(jsonPath("$.viewerCount").value(0));
        mvc.perform(patch("/api/broadcasts/" + id).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"category\":\"GAME\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("GAME"));
        mvc.perform(patch("/api/broadcasts/" + id).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t2\"}"))
                .andExpect(jsonPath("$.category").value("GAME"));
        mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"category\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/broadcasts?category=NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void 카테고리_검색어_소유자_필터를_조합한다() throws Exception {
        long game = create("{\"title\":\"Minecraft 생존\",\"category\":\"GAME\"}");
        long music = create("{\"title\":\"피아노 연주\",\"category\":\"MUSIC\"}");
        create("{\"title\":\"잡담\"}");
        String mine = "/api/broadcasts?ownerId=" + ownerId;

        mvc.perform(get(mine)).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get(mine + "&category=GAME"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(game));
        // 제목 부분일치, 대소문자 무시
        mvc.perform(get(mine + "&q=MINECRAFT"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(game));
        // 방송자 username으로도 찾는다(대문자로 넣어도)
        mvc.perform(get("/api/broadcasts?q=" + ownerName.toUpperCase())).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get(mine + "&q=피아노&category=MUSIC"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(music));
        mvc.perform(get(mine + "&q=피아노&category=GAME")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(mine + "&status=LIVE")).andExpect(jsonPath("$", hasSize(0)));
        // %는 와일드카드가 아니라 글자
        mvc.perform(get(mine + "&q=%25")).andExpect(jsonPath("$", hasSize(0)));
    }
}
