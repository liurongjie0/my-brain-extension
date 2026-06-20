package com.agentplatform.chat.dto;

public record ChatRequest(Long agentId, Long conversationId, String message, String userId) {}
