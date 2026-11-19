package com.livestream.broadcast;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long>, JpaSpecificationExecutor<Broadcast> {
    List<Broadcast> findAllByStatus(Broadcast.Status status);
    List<Broadcast> findAllByStreamKeyIn(Collection<String> streamKeys);
    Optional<Broadcast> findByStreamKey(String streamKey);
    Optional<Broadcast> findFirstByOwnerIdAndStatusOrderByIdDesc(Long ownerId, Broadcast.Status status);
    List<Broadcast> findAllByOwnerIdInAndStatus(Collection<Long> ownerIds, Broadcast.Status status);
}
