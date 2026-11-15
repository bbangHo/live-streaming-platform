package com.livestream.comment;

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

/** 댓글 CRUD + 작성자 인가: 조회 공개 / 작성 로그인 / 수정·삭제 작성자만(방송 소유자도 남의 댓글은 불가). */
class CommentTest extends ApiTestSupport {
    String broadcaster;
    String writer;
    String other;
    long broadcastId;

    @BeforeEach
    void setUp() throws Exception {
        broadcaster = accessTokenOfNewUser();
        writer = accessTokenOfNewUser();
        other = accessTokenOfNewUser();
        String body = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, bearer(broadcaster))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"댓글 방송\"}"))
                .andReturn().getResponse().getContentAsString();
        broadcastId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions write(String token, String content) throws Exception {
        var req = post("/api/broadcasts/" + broadcastId + "/comments")
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}");
        return mvc.perform(token == null ? req : req.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long writeOk(String token, String content) throws Exception {
        String body = write(token, content).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions edit(String token, long id, String content) throws Exception {
        var req = patch("/api/comments/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}");
        return mvc.perform(token == null ? req : req.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions remove(String token, long id) throws Exception {
        var req = delete("/api/comments/" + id);
        return mvc.perform(token == null ? req : req.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    @Test
    void 작성하면_비로그인도_목록에서_본다_오래된_순() throws Exception {
        long first = writeOk(writer, "첫 댓글");
        writeOk(other, "둘째 댓글");
        mvc.perform(get("/api/broadcasts/" + broadcastId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$[0].authorUsername").isString())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("$[1].content").value("둘째 댓글"));
        // after: 새 댓글만
        mvc.perform(get("/api/broadcasts/" + broadcastId + "/comments?after=" + first))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("둘째 댓글"));
    }

    @Test
    void 비로그인은_작성_수정_삭제_401() throws Exception {
        long id = writeOk(writer, "x");
        write(null, "y").andExpect(status().isUnauthorized());
        edit(null, id, "z").andExpect(status().isUnauthorized());
        remove(null, id).andExpect(status().isUnauthorized());
    }

    @Test
    void 타인과_방송_소유자도_남의_댓글_수정_삭제는_403이고_그대로() throws Exception {
        long id = writeOk(writer, "원래 내용");
        edit(other, id, "바꿈").andExpect(status().isForbidden());
        remove(other, id).andExpect(status().isForbidden());
        edit(broadcaster, id, "바꿈").andExpect(status().isForbidden());
        remove(broadcaster, id).andExpect(status().isForbidden());
        mvc.perform(get("/api/broadcasts/" + broadcastId + "/comments"))
                .andExpect(jsonPath("$[0].content").value("원래 내용"));
    }

    @Test
    void 작성자는_수정_삭제_가능() throws Exception {
        long id = writeOk(writer, "원래 내용");
        edit(writer, id, "고친 내용").andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고친 내용"))
                .andExpect(jsonPath("$.updatedAt").isString());
        remove(writer, id).andExpect(status().isNoContent());
        mvc.perform(get("/api/broadcasts/" + broadcastId + "/comments")).andExpect(jsonPath("$", hasSize(0)));
        remove(writer, id).andExpect(status().isNotFound());
    }

    @Test
    void 빈_내용_긴_내용_없는_방송() throws Exception {
        write(writer, "   ").andExpect(status().isBadRequest());
        write(writer, "x".repeat(501)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/broadcasts/999999999/comments").header(HttpHeaders.AUTHORIZATION, bearer(writer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/broadcasts/999999999/comments")).andExpect(status().isNotFound());
    }

    @Test
    void 방송을_지우면_댓글도_지워진다() throws Exception {
        long id = writeOk(writer, "x");
        mvc.perform(delete("/api/broadcasts/" + broadcastId).header(HttpHeaders.AUTHORIZATION, bearer(broadcaster)))
                .andExpect(status().isNoContent());
        edit(writer, id, "y").andExpect(status().isNotFound());
    }
}
