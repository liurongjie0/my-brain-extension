package com.agentplatform.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
        @NotBlank(message = "name 不能为空") String name,
        String description,
        String avatar,
        String systemPrompt,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        String agentType,
        Long modelConfigId,
        Boolean enabled
) {}
