package com.livestream.payment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스페이먼츠 결제 승인 API (POST /v1/payments/confirm). 테스트 키(test_sk_...)면 실제 돈이 나가지 않는다.
 * https://docs.tosspayments.com/reference#결제-승인
 */
public class TossPaymentGateway implements PaymentGateway {
    private final RestClient http;
    private final String clientKey;

    /** builder: 타임아웃 등은 호출하는 쪽(PaymentConfig)이 정한다. 테스트는 MockRestServiceServer를 붙인 builder를 넘긴다. */
    public TossPaymentGateway(PaymentProperties props, RestClient.Builder builder) {
        String basic = Base64.getEncoder().encodeToString((props.tossSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.http = builder.baseUrl(props.tossApiUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic).build();
        this.clientKey = props.tossClientKey();
    }

    @Override public String provider() { return "toss"; }
    @Override public String clientKey() { return clientKey; }

    @Override
    @SuppressWarnings("unchecked")
    public Result confirm(String paymentKey, String orderId, int amount) {
        try {
            return http.post().uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", orderId) // 같은 주문 승인을 두 번 보내도 토스가 한 번만 처리
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .exchange((req, res) -> {
                        Map<String, Object> body = res.bodyTo(Map.class);
                        HttpStatusCode code = res.getStatusCode();
                        if (code.is2xxSuccessful() && body != null && "DONE".equals(body.get("status"))
                                && orderId.equals(body.get("orderId"))) {
                            return Result.ok(((Number) body.get("totalAmount")).intValue());
                        }
                        if (code.is5xxServerError()) throw new GatewayException("토스 서버 오류 " + code.value(), null);
                        String msg = body == null ? "응답 없음" : body.getOrDefault("code", "") + " " + body.getOrDefault("message", "");
                        return Result.fail(msg.trim());
                    });
        } catch (RestClientException e) {
            throw new GatewayException("토스 승인 요청 실패: " + e.getMessage(), e);
        }
    }
}
