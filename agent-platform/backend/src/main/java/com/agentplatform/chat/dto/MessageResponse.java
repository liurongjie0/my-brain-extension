package com.agentplatform.chat.dto;

import com.agentplatform.chat.MessageEntity;

import java.time.LocalDateTime;

public record MessageResponse(Long id, String role, String content, LocalDateTime createdAt) {
    public static MessageResponse from(MessageEntity m) {
        return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}
