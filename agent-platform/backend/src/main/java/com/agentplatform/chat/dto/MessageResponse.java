package com.agentplatform.chat.dto;

import com.agentplatform.chat.MessageEntity;

import java.time.LocalDateTime;

public record MessageResponse(Long id, String role, String content, String toolCalls, LocalDateTime createdAt) {
    public static MessageResponse from(MessageEntity m) {
        // toolCalls is the persisted ReAct trajectory JSON ([{tool,args,result}, ...]); the
        // client parses it to replay the tool-call drill-down on reopened conversations.
        return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getToolCallsJson(), m.getCreatedAt());
    }
}
