package com.livestream.payment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 토스 키가 없을 때 쓰는 내장 테스트 PG. 실제 PG처럼 "결제창에서 카드 승인 → paymentKey 발급 → 가맹점 서버가 승인 확인" 순서를 따른다.
 * 결제창(test-checkout.html)이 authorize()로 paymentKey를 받고, 백엔드는 confirm()에서 그 키의 주문·금액이 맞는지 확인한다.
 * 테스트 카드: 4242 4242 4242 4242 = 승인, 4000 0000 0000 0002 = 거절. 메모리에만 둔다(재시작하면 미승인 결제창은 무효).
 */
public class TestPaymentGateway implements PaymentGateway {
    public static final String DECLINED_CARD = "4000000000000002";

    private record Authorization(String orderId, int amount, boolean declined) {}

    private final Map<String, Authorization> authorizations = new ConcurrentHashMap<>();

    @Override public String provider() { return "test"; }
    @Override public String clientKey() { return null; }

    /** 결제창에서 카드 결제 → paymentKey 발급 (아직 가맹점 승인 전). */
    public String authorize(String orderId, int amount, String cardNumber) {
        String key = "test_pay_" + UUID.randomUUID().toString().replace("-", "");
        authorizations.put(key, new Authorization(orderId, amount, DECLINED_CARD.equals(cardNumber)));
        return key;
    }

    @Override
    public Result confirm(String paymentKey, String orderId, int amount) {
        Authorization a = authorizations.get(paymentKey);
        if (a == null) return Result.fail("NOT_FOUND_PAYMENT 존재하지 않는 결제");
        if (!a.orderId().equals(orderId) || a.amount() != amount) return Result.fail("INVALID_REQUEST 주문 정보 불일치");
        if (a.declined()) return Result.fail("REJECT_CARD_PAYMENT 카드 승인 거절 (테스트 거절 카드)");
        return Result.ok(a.amount());
    }
}
