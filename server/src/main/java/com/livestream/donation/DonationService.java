package com.livestream.donation;

import com.livestream.broadcast.Broadcast;
import com.livestream.broadcast.BroadcastService;
import com.livestream.payment.PaymentGateway;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 후원 = 주문 생성 → (결제창) → 서버 승인 확인.
 * 서버 검증: 주문 금액과 결제창이 돌려준 금액이 같은지, 주문자 본인인지, PG가 실제로 승인했는지(승인 금액까지) 확인한다.
 * 중복 적립 방지: 주문 행을 잠그고(FOR UPDATE) PENDING일 때만 DONE으로 한 번 바꾼다. 이미 DONE이면 같은 결과를 그대로 돌려줄 뿐
 * 다시 더하지 않는다. payment_key는 UNIQUE, PG에는 Idempotency-Key = 주문 번호. 합계는 DONE 행의 합.
 */
@Service
public class DonationService {
    private static final Logger log = LoggerFactory.getLogger(DonationService.class);
    public static final int MIN_AMOUNT = 1_000;
    public static final int MAX_AMOUNT = 1_000_000;

    private final DonationRepository donations;
    private final BroadcastService broadcasts;
    private final PaymentGateway gateway;
    private final Clock clock;

    public DonationService(DonationRepository donations, BroadcastService broadcasts, PaymentGateway gateway, Clock clock) {
        this.donations = donations;
        this.broadcasts = broadcasts;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional
    public Donation createOrder(Long userId, Long broadcastId, int amount, String message) {
        Broadcast b = broadcasts.get(broadcastId);
        if (b.isOwnedBy(userId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인 방송에는 후원할 수 없음");
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "후원 금액은 1,000원 ~ 1,000,000원");
        }
        String msg = message == null || message.isBlank() ? null : message.strip();
        return donations.save(new Donation(broadcastId, userId, amount, msg, gateway.provider(), clock.instant()));
    }

    /**
     * 결제창에서 돌아온 결과를 승인한다. 반환 상태: DONE(적립) 또는 FAILED(PG 거절).
     * 같은 요청이 여러 번 와도(새로고침·동시 요청) 적립은 한 번뿐이다.
     */
    @Transactional
    public Donation confirm(Long userId, String orderId, String paymentKey, int amount) {
        Donation d = donations.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문 없음"));
        if (!d.getDonorId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 주문만 승인할 수 있음");

        if (d.getStatus() == Donation.Status.DONE) {
            if (d.getPaymentKey().equals(paymentKey)) return d; // 이미 승인됨 → 다시 적립하지 않고 같은 결과
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 결제로 승인된 주문");
        }
        if (d.getStatus() == Donation.Status.FAILED) throw new ResponseStatusException(HttpStatus.CONFLICT, "실패한 주문 — 새로 후원하세요");

        // 결제창 쪽 금액 조작 방지: 주문을 만들 때 서버가 정한 금액과 같아야 PG에 승인을 요청한다
        if (amount != d.getAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 다름");
        }
        if (donations.existsByPaymentKey(paymentKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용된 결제 키");
        }

        PaymentGateway.Result r;
        try {
            r = gateway.confirm(paymentKey, orderId, amount);
        } catch (PaymentGateway.GatewayException e) {
            log.warn("PG 승인 요청 실패 {}: {}", orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제사 응답 없음 — 잠시 후 다시 시도");
        }
        if (r.approved() && r.approvedAmount() == d.getAmount()) {
            d.approve(paymentKey, clock.instant());
            log.info("후원 승인: {} 방송 #{} {}원", orderId, d.getBroadcastId(), d.getAmount());
        } else {
            d.fail(paymentKey, r.approved() ? "승인 금액 불일치 " + r.approvedAmount() : r.failReason());
            log.info("후원 실패: {} {}", orderId, d.getFailReason());
        }
        return d;
    }

    @Transactional(readOnly = true)
    public Donation getOwnOrder(Long userId, String orderId) {
        Donation d = donations.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문 없음"));
        if (!d.getDonorId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 주문만 볼 수 있음");
        return d;
    }

    public record Summary(long total, long count, List<Donation> recent) {}

    @Transactional(readOnly = true)
    public Summary summary(Long broadcastId) {
        broadcasts.get(broadcastId);
        return new Summary(donations.sumAmount(broadcastId, Donation.Status.DONE),
                donations.countByBroadcastIdAndStatus(broadcastId, Donation.Status.DONE),
                donations.findTop20ByBroadcastIdAndStatusOrderByApprovedAtDesc(broadcastId, Donation.Status.DONE));
    }
}
