package com.agentplatform.agent.dto;

public record AgentRequest(
        String name,
        String description,
        String avatar,
        String systemPrompt,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String agentType,
        Boolean enabled
) {}
