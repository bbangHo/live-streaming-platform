package com.livestream.broadcast;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {
    List<Broadcast> findAllByOrderByIdDesc();
}
