package com.livestream.channel;

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
import org.springframework.test.web.servlet.ResultActions;

/** 팔로우 멱등·자기 팔로우 금지, 채널 게시글 삭제 인가(작성자·채널 주인만). */
class ChannelTest extends ApiTestSupport {
    String owner;
    String writer;
    String other;
    long ownerId;
    long writerId;

    @BeforeEach
    void setUp() throws Exception {
        owner = accessTokenOfNewUser();
        writer = accessTokenOfNewUser();
        other = accessTokenOfNewUser();
        ownerId = idOf(owner);
        writerId = idOf(writer);
    }

    private long idOf(String token) throws Exception {
        String me = mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(me, "$.id")).longValue();
    }

    private ResultActions as(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        return mvc.perform(token == null ? req : req.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long writePost(String token, long channelId, String content) throws Exception {
        String body = as(token, post("/api/channels/" + channelId + "/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void 팔로우는_멱등이고_수와_following에_반영() throws Exception {
        as(writer, put("/api/channels/" + ownerId + "/follow")).andExpect(status().isNoContent());
        as(writer, put("/api/channels/" + ownerId + "/follow")).andExpect(status().isNoContent());
        as(writer, get("/api/channels/" + ownerId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.liveBroadcastId").doesNotExist());
        mvc.perform(get("/api/channels/" + ownerId)).andExpect(jsonPath("$.following").value(false));
        as(writer, get("/api/me/following")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownerId));

        as(writer, delete("/api/channels/" + ownerId + "/follow")).andExpect(status().isNoContent());
        as(writer, delete("/api/channels/" + ownerId + "/follow")).andExpect(status().isNoContent());
        mvc.perform(get("/api/channels/" + ownerId)).andExpect(jsonPath("$.followerCount").value(0));
        as(writer, get("/api/me/following")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 자기_팔로우_400_없는_채널_404_비로그인_401() throws Exception {
        as(owner, put("/api/channels/" + ownerId + "/follow")).andExpect(status().isBadRequest());
        as(owner, put("/api/channels/999999999/follow")).andExpect(status().isNotFound());
        as(null, put("/api/channels/" + ownerId + "/follow")).andExpect(status().isUnauthorized());
        as(null, get("/api/me/following")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/channels/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void 팔로우_목록은_라이브_먼저() throws Exception {
        long liveCh = idOf(other);
        as(writer, put("/api/channels/" + ownerId + "/follow")).andExpect(status().isNoContent());
        as(writer, put("/api/channels/" + liveCh + "/follow")).andExpect(status().isNoContent());
        String b = as(other, post("/api/broadcasts").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"켠 방송\"}"))
                .andReturn().getResponse().getContentAsString();
        long broadcastId = ((Number) JsonPath.read(b, "$.id")).longValue();
        goLive(broadcastId);
        as(writer, get("/api/me/following"))
                .andExpect(jsonPath("$[0].id").value(liveCh))
                .andExpect(jsonPath("$[0].liveBroadcastId").value(broadcastId))
                .andExpect(jsonPath("$[0].liveTitle").value("켠 방송"))
                .andExpect(jsonPath("$[1].liveBroadcastId").doesNotExist());
        mvc.perform(get("/api/channels/" + liveCh)).andExpect(jsonPath("$.liveBroadcastId").value(broadcastId));
    }

    @Test
    void 게시글_작성자와_채널_주인은_삭제_가능() throws Exception {
        long byWriter = writePost(writer, ownerId, "작성자가 지움");
        as(writer, delete("/api/posts/" + byWriter)).andExpect(status().isNoContent());
        long byWriter2 = writePost(writer, ownerId, "주인이 지움");
        as(owner, delete("/api/posts/" + byWriter2)).andExpect(status().isNoContent());
        mvc.perform(get("/api/channels/" + ownerId + "/posts")).andExpect(jsonPath("$", hasSize(0)));
        as(owner, delete("/api/posts/" + byWriter2)).andExpect(status().isNotFound());
    }

    @Test
    void 타인은_게시글_삭제_403이고_그대로() throws Exception {
        long id = writePost(writer, ownerId, "남아 있어야 함");
        as(other, delete("/api/posts/" + id)).andExpect(status().isForbidden());
        as(null, delete("/api/posts/" + id)).andExpect(status().isUnauthorized());
        // 다른 채널 주인도 남의 채널 글은 못 지운다
        long elsewhere = writePost(owner, writerId, "작성자 채널의 글");
        as(other, delete("/api/posts/" + elsewhere)).andExpect(status().isForbidden());
        mvc.perform(get("/api/channels/" + ownerId + "/posts"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("남아 있어야 함"))
                .andExpect(jsonPath("$[0].channelId").value(ownerId))
                .andExpect(jsonPath("$[0].authorId").value(writerId))
                .andExpect(jsonPath("$[0].authorUsername").isString());
    }

    @Test
    void 게시글_검증() throws Exception {
        as(writer, post("/api/channels/" + ownerId + "/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"   \"}")).andExpect(status().isBadRequest());
        as(writer, post("/api/channels/" + ownerId + "/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + "x".repeat(1001) + "\"}")).andExpect(status().isBadRequest());
        as(null, post("/api/channels/" + ownerId + "/posts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/channels/999999999/posts")).andExpect(status().isNotFound());
    }
}
