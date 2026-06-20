package com.agentplatform.tool.dto;

import com.agentplatform.tool.ToolEntity;

import java.time.LocalDateTime;

public record ToolResponse(Long id, String name, String description, String method, String url,
                           String headersJson, String paramsSchemaJson, Boolean enabled,
                           LocalDateTime createdAt) {
    public static ToolResponse from(ToolEntity e) {
        return new ToolResponse(e.getId(), e.getName(), e.getDescription(), e.getMethod(),
                e.getUrl(), e.getHeadersJson(), e.getParamsSchemaJson(), e.getEnabled(), e.getCreatedAt());
    }
}
