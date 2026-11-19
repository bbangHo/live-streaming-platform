package com.livestream.channel;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelPostRepository extends JpaRepository<ChannelPost, Long> {
    // shortcut: 최신 50개만, 페이지네이션 없음
    List<ChannelPost> findTop50ByChannelIdOrderByIdDesc(Long channelId);
}
