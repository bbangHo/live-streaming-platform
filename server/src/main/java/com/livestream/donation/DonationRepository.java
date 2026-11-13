package com.livestream.donation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    /** 승인 처리용: 같은 주문의 승인 요청이 동시에 와도 한 줄로 세운다 (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.orderId = :orderId")
    Optional<Donation> findByOrderIdForUpdate(@Param("orderId") String orderId);

    Optional<Donation> findByOrderId(String orderId);

    boolean existsByPaymentKey(String paymentKey);

    /** 합계는 매번 승인된 행에서 계산한다 — 카운터를 따로 올리지 않으니 이중 적립이 생길 수 없다. */
    @Query("select coalesce(sum(d.amount), 0) from Donation d where d.broadcastId = :broadcastId and d.status = :status")
    long sumAmount(@Param("broadcastId") Long broadcastId, @Param("status") Donation.Status status);

    long countByBroadcastIdAndStatus(Long broadcastId, Donation.Status status);

    List<Donation> findTop20ByBroadcastIdAndStatusOrderByApprovedAtDesc(Long broadcastId, Donation.Status status);
}
