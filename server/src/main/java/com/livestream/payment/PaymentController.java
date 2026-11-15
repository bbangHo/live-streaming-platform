package com.livestream.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PaymentController {
    private final PaymentGateway gateway;

    public PaymentController(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    /** 화면이 어떤 결제창을 띄울지: {provider: "toss"|"test", clientKey} (공개). */
    @GetMapping("/api/payments/config")
    public Map<String, Object> config() {
        return gateway.clientKey() == null ? Map.of("provider", gateway.provider())
                : Map.of("provider", gateway.provider(), "clientKey", gateway.clientKey());
    }

    public record TestPayRequest(@NotBlank String orderId, @Min(1) int amount, @Pattern(regexp = "\\d{16}") String cardNumber) {}

    /** 내장 테스트 PG의 "결제창" 승인 (test-checkout.html). 토스 모드에서는 404. 실제 적립은 /api/donations/confirm에서만. */
    @PostMapping("/api/test-pg/pay")
    public Map<String, String> testPay(@Valid @RequestBody TestPayRequest req) {
        if (!(gateway instanceof TestPaymentGateway test)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return Map.of("paymentKey", test.authorize(req.orderId(), req.amount(), req.cardNumber()));
    }
}
