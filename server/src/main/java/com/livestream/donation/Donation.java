package com.livestream.donation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 후원 한 건 = 주문. PENDING(결제 전) → DONE(PG 승인 확인, 합계에 포함) | FAILED(PG 거절). DONE·FAILED는 다시 바뀌지 않는다. */
@Entity
@Table(name = "donations")
public class Donation {
    public enum Status { PENDING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;

    private Long broadcastId;

    private Long donorId;

    private int amount;

    private String message;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private String provider;

    private String paymentKey;

    private String failReason;

    private Instant createdAt;

    private Instant approvedAt;

    protected Donation() {}

    public Donation(Long broadcastId, Long donorId, int amount, String message, String provider, Instant now) {
        this.orderId = "don_" + UUID.randomUUID().toString().replace("-", ""); // 토스 규칙: 영문·숫자·-·_ 6~64자
        this.broadcastId = broadcastId;
        this.donorId = donorId;
        this.amount = amount;
        this.message = message;
        this.provider = provider;
        this.createdAt = now;
    }

    void approve(String paymentKey, Instant now) {
        this.status = Status.DONE;
        this.paymentKey = paymentKey;
        this.approvedAt = now;
    }

    void fail(String paymentKey, String reason) {
        this.status = Status.FAILED;
        this.paymentKey = paymentKey;
        this.failReason = reason;
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public Long getBroadcastId() { return broadcastId; }
    public Long getDonorId() { return donorId; }
    public int getAmount() { return amount; }
    public String getMessage() { return message; }
    public Status getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getPaymentKey() { return paymentKey; }
    public String getFailReason() { return failReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
