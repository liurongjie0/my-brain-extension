package com.agentplatform.agent.dto;

import com.agentplatform.agent.AgentEntity;

import java.time.LocalDateTime;

public record AgentResponse(
        Long id,
        String name,
        String description,
        String avatar,
        String systemPrompt,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String agentType,
        Long modelConfigId,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AgentResponse from(AgentEntity e) {
        return new AgentResponse(
                e.getId(), e.getName(), e.getDescription(), e.getAvatar(),
                e.getSystemPrompt(), e.getModel(), e.getTemperature(), e.getTopP(),
                e.getMaxTokens(), e.getAgentType(), e.getModelConfigId(), e.getEnabled(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
