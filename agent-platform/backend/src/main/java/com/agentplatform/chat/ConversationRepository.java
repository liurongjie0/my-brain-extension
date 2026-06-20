package com.agentplatform.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<ConversationEntity> findAllByOrderByUpdatedAtDesc();
}
