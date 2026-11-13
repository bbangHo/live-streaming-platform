package com.livestream.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 토스 승인 API 호출 형식(Basic 인증·Idempotency-Key·본문)과 응답 해석. 실제 토스 서버 대신 가짜 응답. */
class TossPaymentGatewayTest {
    MockRestServiceServer toss;
    TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        toss = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(new PaymentProperties("test_ck_x", "test_sk_secret", "https://api.tosspayments.com"), builder);
    }

    @Test
    void 승인_요청_형식과_성공_응답() {
        toss.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic dGVzdF9za19zZWNyZXQ6")) // base64("test_sk_secret:")
                .andExpect(header("Idempotency-Key", "don_1"))
                .andExpect(jsonPath("$.paymentKey").value("pk_1"))
                .andExpect(jsonPath("$.orderId").value("don_1"))
                .andExpect(jsonPath("$.amount").value(5000))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"orderId\":\"don_1\",\"totalAmount\":5000}", MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm("pk_1", "don_1", 5000)).isEqualTo(PaymentGateway.Result.ok(5000));
        toss.verify();
    }

    @Test
    void 거절_응답은_실패_사유와_함께() {
        toss.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"REJECT_CARD_PAYMENT\",\"message\":\"한도초과\"}"));
        PaymentGateway.Result r = gateway.confirm("pk_1", "don_1", 5000);
        assertThat(r.approved()).isFalse();
        assertThat(r.failReason()).isEqualTo("REJECT_CARD_PAYMENT 한도초과");
    }

    @Test
    void 토스_서버_오류는_예외_승인_여부_모름() {
        toss.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.confirm("pk_1", "don_1", 5000)).isInstanceOf(PaymentGateway.GatewayException.class);
    }
}
