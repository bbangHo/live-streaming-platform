package com.livestream.donation;

import com.livestream.broadcast.BroadcastService;
import com.livestream.payment.PaymentGateway;
import com.livestream.user.User;
import com.livestream.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 합계·최근 후원은 공개, 주문·승인은 로그인. */
@RestController
public class DonationController {
    private final DonationService service;
    private final BroadcastService broadcasts;
    private final PaymentGateway gateway;
    private final UserRepository users;

    public DonationController(DonationService service, BroadcastService broadcasts, PaymentGateway gateway, UserRepository users) {
        this.service = service;
        this.broadcasts = broadcasts;
        this.gateway = gateway;
        this.users = users;
    }

    public record OrderRequest(@NotNull Integer amount, @Size(max = 200) String message) {}

    /** 결제창에 넘길 값. provider가 toss면 clientKey로 토스 결제창, test면 내장 테스트 결제창. */
    public record OrderResponse(String orderId, int amount, String orderName, String provider, String clientKey) {}

    public record ConfirmRequest(@NotBlank String orderId, @NotBlank @Size(max = 200) String paymentKey, @NotNull Integer amount) {}

    public record DonationResponse(String orderId, Long broadcastId, String donorUsername, int amount, String message,
                                   Donation.Status status, String failReason, Instant approvedAt) {}

    public record SummaryResponse(long total, long count, List<DonationResponse> recent) {}

    @PostMapping("/api/broadcasts/{broadcastId}/donations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse order(@AuthenticationPrincipal Long userId, @PathVariable Long broadcastId,
                               @Valid @RequestBody OrderRequest req) {
        Donation d = service.createOrder(userId, broadcastId, req.amount(), req.message());
        String title = broadcasts.get(broadcastId).getTitle();
        String orderName = "후원: " + (title.length() > 40 ? title.substring(0, 40) + "…" : title);
        return new OrderResponse(d.getOrderId(), d.getAmount(), orderName, gateway.provider(), gateway.clientKey());
    }

    /** 승인. 거절이면 402 + 본문에 실패 사유 (주문은 FAILED로 남는다). 같은 요청 반복은 같은 결과. */
    @PostMapping("/api/donations/confirm")
    public ResponseEntity<DonationResponse> confirm(@AuthenticationPrincipal Long userId, @Valid @RequestBody ConfirmRequest req) {
        Donation d = service.confirm(userId, req.orderId(), req.paymentKey(), req.amount());
        return ResponseEntity.status(d.getStatus() == Donation.Status.DONE ? HttpStatus.OK : HttpStatus.PAYMENT_REQUIRED)
                .body(toResponse(d, usernames(List.of(d))));
    }

    @GetMapping("/api/donations/{orderId}")
    public DonationResponse get(@AuthenticationPrincipal Long userId, @PathVariable String orderId) {
        Donation d = service.getOwnOrder(userId, orderId);
        return toResponse(d, usernames(List.of(d)));
    }

    @GetMapping("/api/broadcasts/{broadcastId}/donations")
    public SummaryResponse summary(@PathVariable Long broadcastId) {
        DonationService.Summary s = service.summary(broadcastId);
        Map<Long, String> names = usernames(s.recent());
        return new SummaryResponse(s.total(), s.count(), s.recent().stream().map(d -> toResponse(d, names)).toList());
    }

    private Map<Long, String> usernames(List<Donation> list) {
        return users.findAllById(list.stream().map(Donation::getDonorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private static DonationResponse toResponse(Donation d, Map<Long, String> names) {
        return new DonationResponse(d.getOrderId(), d.getBroadcastId(), names.get(d.getDonorId()), d.getAmount(),
                d.getMessage(), d.getStatus(), d.getFailReason(), d.getApprovedAt());
    }
}
