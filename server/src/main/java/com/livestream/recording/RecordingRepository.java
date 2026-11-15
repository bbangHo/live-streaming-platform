package com.livestream.recording;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingRepository extends JpaRepository<Recording, Long> {
    // shortcut: 페이지네이션 없음 — 최근 200개만
    List<Recording> findTop200ByOrderByStartedAtDesc();
    List<Recording> findAllByBroadcastIdOrderByStartedAtAsc(Long broadcastId);
    Optional<Recording> findByFilePath(String filePath);
}
