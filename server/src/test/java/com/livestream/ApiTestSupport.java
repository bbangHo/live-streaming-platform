package com.livestream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.stream.MediaMtxClient;
import com.livestream.stream.StreamStatusSync;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.media.sync-enabled=false",   // 실제 MediaMTX 없이 돈다. 상태 동기화는 reconcile()을 직접 불러 검증
        "app.media.hook-secret=" + ApiTestSupport.HOOK_SECRET,
        "app.media.dev-bypass=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class ApiTestSupport {
    public static final String HOOK_SECRET = "test-hook-secret";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected StreamStatusSync sync;

    /** MediaMTX에 이 스트림 키로 송출이 들어와 있는 상황을 흉내 내 상태 동기화를 한 번 돌린다. */
    protected List<MediaMtxClient.OnlinePath> syncWithOnline(Instant now, String... streamKeys) {
        return sync.reconcile(Arrays.stream(streamKeys)
                .map(k -> new MediaMtxClient.OnlinePath("live/" + k, "webRTCSession", "session-" + k)).toList(), now);
    }

    protected String streamKeyOf(long broadcastId) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/broadcasts/" + broadcastId)).andReturn().getResponse().getContentAsString(),
                "$.streamKey");
    }

    /** 송출이 들어와 방송 중이 된 상태로 만든다. */
    protected void goLive(long broadcastId) throws Exception {
        syncWithOnline(Instant.now(), streamKeyOf(broadcastId));
    }

    protected static String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected MvcResult postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    /** 새 사용자를 가입·로그인시키고 로그인 응답 본문을 돌려준다. */
    protected String signupAndLogin() throws Exception {
        String name = uniqueName();
        String body = "{\"username\":\"" + name + "\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    protected String accessTokenOfNewUser() throws Exception {
        return JsonPath.read(signupAndLogin(), "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
