package com.livestream.recording;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecordingRepository extends JpaRepository<Recording, Long> {
    // shortcut: 페이지네이션 없음 — 최근 200개만
    List<Recording> findTop200ByOrderByStartedAtDesc();
    @Query("select r from Recording r where r.broadcastId in (select b.id from Broadcast b where b.ownerId = :ownerId) order by r.startedAt desc")
    List<Recording> findByOwner(Long ownerId, Limit limit);
    List<Recording> findAllByBroadcastIdOrderByStartedAtAsc(Long broadcastId);
    Optional<Recording> findByFilePath(String filePath);
}
