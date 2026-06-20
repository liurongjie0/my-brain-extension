package com.agentplatform.chat.dto;

import com.agentplatform.chat.ConversationEntity;

import java.time.LocalDateTime;

public record ConversationResponse(Long id, Long agentId, String title, LocalDateTime updatedAt) {
    public static ConversationResponse from(ConversationEntity c) {
        return new ConversationResponse(c.getId(), c.getAgentId(), c.getTitle(), c.getUpdatedAt());
    }
}
