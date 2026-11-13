package com.livestream.payment;

/** PG(결제대행) 승인 요청. 구현: TossPaymentGateway(토스페이먼츠 테스트 키), TestPaymentGateway(키가 없을 때 내장 테스트 PG). */
public interface PaymentGateway {
    /** "toss" | "test" — 화면이 어떤 결제창을 띄울지 정한다. */
    String provider();

    /** 화면용 공개 키 (토스 client key). 테스트 PG는 null. */
    String clientKey();

    /**
     * 결제 승인. 결제창에서 돌아온 paymentKey를 PG에 확인받는다. 같은 orderId로 여러 번 불러도 PG가 한 번만 승인한다(Idempotency-Key).
     * @return 승인 결과. 거절이면 approved=false
     */
    Result confirm(String paymentKey, String orderId, int amount);

    record Result(boolean approved, int approvedAmount, String failReason) {
        public static Result ok(int amount) { return new Result(true, amount, null); }
        public static Result fail(String reason) { return new Result(false, 0, reason); }
    }

    /** PG와 통신 자체가 실패(네트워크·5xx). 승인 여부를 모르므로 주문은 PENDING으로 두고 다시 시도하게 한다. */
    class GatewayException extends RuntimeException {
        public GatewayException(String message, Throwable cause) { super(message, cause); }
    }
}
