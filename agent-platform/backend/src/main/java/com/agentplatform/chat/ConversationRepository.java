package com.agentplatform.chat;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<ConversationEntity> findAllByOrderByUpdatedAtDesc();

    /** Bump updated_at so recency ordering reflects the latest activity. */
    @Modifying
    @Transactional
    @Query("update ConversationEntity c set c.updatedAt = CURRENT_TIMESTAMP where c.id = :id")
    void touch(Long id);
}
