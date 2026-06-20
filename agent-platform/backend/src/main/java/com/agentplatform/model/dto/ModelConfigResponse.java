package com.agentplatform.model.dto;

import com.agentplatform.model.ModelConfigEntity;

import java.time.LocalDateTime;

public record ModelConfigResponse(
        Long id,
        String name,
        String baseUrl,
        String apiKeyMasked,
        String model,
        Boolean enabled,
        LocalDateTime createdAt
) {
    public static ModelConfigResponse from(ModelConfigEntity e) {
        return new ModelConfigResponse(e.getId(), e.getName(), e.getBaseUrl(),
                mask(e.getApiKey()), e.getModel(), e.getEnabled(), e.getCreatedAt());
    }

    private static String mask(String key) {
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
