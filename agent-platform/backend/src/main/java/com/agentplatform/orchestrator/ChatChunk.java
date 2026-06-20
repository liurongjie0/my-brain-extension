package com.agentplatform.orchestrator;

public record ChatChunk(Long conversationId, String type, String content) {}
