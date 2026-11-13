package com.livestream.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** MediaMTX 송출 상태 → 방송 상태 동기화 (유예 30초). 시각을 직접 넣어 검증한다. */
class StreamStatusSyncTest extends ApiTestSupport {
    String owner;
    long id;
    String key;
    Instant t0;

    @BeforeEach
    void setUp() throws Exception {
        owner = accessTokenOfNewUser();
        String body = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"동기화\"}"))
                .andReturn().getResponse().getContentAsString();
        id = ((Number) JsonPath.read(body, "$.id")).longValue();
        key = JsonPath.read(body, "$.streamKey");
        t0 = Instant.now();
    }

    private String status() throws Exception {
        return JsonPath.read(mvc.perform(get("/api/broadcasts/" + id)).andReturn().getResponse().getContentAsString(), "$.status");
    }

    @Test
    void 송출이_들어오면_방송_중() throws Exception {
        assertThat(status()).isEqualTo("READY");
        syncWithOnline(t0, key);
        assertThat(status()).isEqualTo("LIVE");
    }

    @Test
    void 끊긴_뒤_30초_안에_다시_붙으면_방송_중_유지() throws Exception {
        syncWithOnline(t0, key);
        syncWithOnline(t0.plusSeconds(2));          // 끊김 (새로고침 등)
        syncWithOnline(t0.plusSeconds(29));
        assertThat(status()).isEqualTo("LIVE");
        syncWithOnline(t0.plusSeconds(30), key);    // 다시 붙음 → 유예 초기화
        syncWithOnline(t0.plusSeconds(40));         // 또 끊김: 여기서부터 다시 30초
        syncWithOnline(t0.plusSeconds(69));
        assertThat(status()).isEqualTo("LIVE");
    }

    @Test
    void 끊긴_채로_30초가_지나면_종료() throws Exception {
        syncWithOnline(t0, key);
        syncWithOnline(t0.plusSeconds(2));
        syncWithOnline(t0.plusSeconds(32));
        assertThat(status()).isEqualTo("ENDED");
        mvc.perform(get("/api/broadcasts/" + id)).andExpect(jsonPath("$.endedAt").isString());
    }

    @Test
    void 소유자가_종료한_방송에_남은_송출은_끊는다() throws Exception {
        syncWithOnline(t0, key);
        mvc.perform(post("/api/broadcasts/" + id + "/stop").header(HttpHeaders.AUTHORIZATION, bearer(owner)));
        List<MediaMtxClient.OnlinePath> kicked = syncWithOnline(t0.plusSeconds(1), key);
        assertThat(kicked).extracting(MediaMtxClient.OnlinePath::name).containsExactly("live/" + key);
        assertThat(status()).isEqualTo("ENDED");
    }

    @Test
    void 등록되지_않은_키의_송출은_devBypass가_꺼져_있으면_끊는다() {
        List<MediaMtxClient.OnlinePath> kicked = syncWithOnline(t0, "unknown-key-" + System.nanoTime());
        assertThat(kicked).hasSize(1);
    }
}
