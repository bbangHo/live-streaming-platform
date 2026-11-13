package com.livestream.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.livestream.ApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 도네이션: 테스트 결제 → 서버 검증 → 중복 적립 방지 → 합계. 키가 없으니 내장 테스트 PG로 돈다. */
class DonationTest extends ApiTestSupport {
    String broadcaster;
    String donor;
    String other;
    long broadcastId;

    @BeforeEach
    void setUp() throws Exception {
        broadcaster = accessTokenOfNewUser();
        donor = accessTokenOfNewUser();
        other = accessTokenOfNewUser();
        String body = mvc.perform(post("/api/broadcasts").header(HttpHeaders.AUTHORIZATION, bearer(broadcaster))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"후원 방송\"}"))
                .andReturn().getResponse().getContentAsString();
        broadcastId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions order(String token, int amount) throws Exception {
        var req = post("/api/broadcasts/" + broadcastId + "/donations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"message\":\"화이팅\"}");
        return mvc.perform(token == null ? req : req.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String orderOk(String token, int amount) throws Exception {
        return JsonPath.read(order(token, amount).andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("test"))
                .andExpect(jsonPath("$.amount").value(amount))
                .andReturn().getResponse().getContentAsString(), "$.orderId");
    }

    /** 테스트 PG 결제창에서 카드 결제 → paymentKey (아직 적립 아님) */
    private String pay(String orderId, int amount, String card) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/test-pg/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"amount\":%d,\"cardNumber\":\"%s\"}".formatted(orderId, amount, card)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.paymentKey");
    }

    private ResultActions confirm(String token, String orderId, String paymentKey, int amount) throws Exception {
        return mvc.perform(post("/api/donations/confirm").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"%s\",\"paymentKey\":\"%s\",\"amount\":%d}".formatted(orderId, paymentKey, amount)));
    }

    private ResultActions summary() throws Exception {
        return mvc.perform(get("/api/broadcasts/" + broadcastId + "/donations"));
    }

    @Test
    void 결제_승인되면_합계에_더해지고_누구나_본다() throws Exception {
        String o1 = orderOk(donor, 5000);
        confirm(donor, o1, pay(o1, 5000, "4242424242424242"), 5000).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        String o2 = orderOk(other, 1000);
        confirm(other, o2, pay(o2, 1000, "4242424242424242"), 1000).andExpect(status().isOk());

        summary().andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6000))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.recent", hasSize(2)))
                .andExpect(jsonPath("$.recent[1].message").value("화이팅"))
                .andExpect(jsonPath("$.recent[1].donorUsername").isString());
    }

    @Test
    void 같은_승인_요청을_반복해도_한_번만_적립() throws Exception {
        String o = orderOk(donor, 3000);
        String key = pay(o, 3000, "4242424242424242");
        confirm(donor, o, key, 3000).andExpect(status().isOk());
        confirm(donor, o, key, 3000).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE")); // 새로고침
        summary().andExpect(jsonPath("$.total").value(3000)).andExpect(jsonPath("$.count").value(1));
        // 같은 주문에 다른 결제 키, 다른 주문에 이미 쓴 결제 키 → 거부
        confirm(donor, o, pay(o, 3000, "4242424242424242"), 3000).andExpect(status().isConflict());
        String o2 = orderOk(donor, 3000);
        confirm(donor, o2, key, 3000).andExpect(status().isConflict());
        summary().andExpect(jsonPath("$.total").value(3000));
    }

    @Test
    void 동시에_승인_요청이_와도_한_번만_적립() throws Exception {
        String o = orderOk(donor, 10000);
        String key = pay(o, 10000, "4242424242424242");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return confirm(donor, o, key, 10000).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        pool.shutdown();
        summary().andExpect(jsonPath("$.total").value(10000)).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void 금액을_바꿔_승인하면_400이고_적립_안_됨() throws Exception {
        String o = orderOk(donor, 50000);
        String key = pay(o, 1000, "4242424242424242");          // 결제창에서 1000원만 결제하고
        confirm(donor, o, key, 1000).andExpect(status().isBadRequest());  // 주문(50000)과 다른 금액으로 승인 요청
        String key2 = pay(o, 1000, "4242424242424242");
        confirm(donor, o, key2, 50000).andExpect(status().isPaymentRequired()) // 금액만 맞춰 보내도 PG 결제 금액이 달라 거절
                .andExpect(jsonPath("$.status").value("FAILED"));
        summary().andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 카드_거절이면_402_실패로_남고_적립_안_됨() throws Exception {
        String o = orderOk(donor, 2000);
        confirm(donor, o, pay(o, 2000, "4000000000000002"), 2000).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failReason").isString());
        confirm(donor, o, pay(o, 2000, "4242424242424242"), 2000).andExpect(status().isConflict()); // 실패한 주문 재사용 불가
        summary().andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 남의_주문_승인은_403_존재하지_않는_결제키는_실패() throws Exception {
        String o = orderOk(donor, 1000);
        confirm(other, o, pay(o, 1000, "4242424242424242"), 1000).andExpect(status().isForbidden());
        confirm(donor, o, "test_pay_forged", 1000).andExpect(status().isPaymentRequired());
        summary().andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 주문_검증_로그인_금액범위_본인방송() throws Exception {
        order(null, 1000).andExpect(status().isUnauthorized());
        order(donor, 999).andExpect(status().isBadRequest());
        order(donor, 1_000_001).andExpect(status().isBadRequest());
        order(broadcaster, 1000).andExpect(status().isBadRequest());
        mvc.perform(post("/api/donations/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"x\",\"paymentKey\":\"y\",\"amount\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/config")).andExpect(jsonPath("$.provider").value("test"));
    }
}
