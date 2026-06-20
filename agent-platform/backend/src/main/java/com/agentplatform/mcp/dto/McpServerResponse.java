package com.agentplatform.mcp.dto;

import com.agentplatform.mcp.McpServerEntity;

import java.time.LocalDateTime;

public record McpServerResponse(
        Long id, String name, String transport, String url,
        String command, String args, Boolean enabled, LocalDateTime createdAt
) {
    public static McpServerResponse from(McpServerEntity e) {
        return new McpServerResponse(e.getId(), e.getName(), e.getTransport(), e.getUrl(),
                e.getCommand(), e.getArgs(), e.getEnabled(), e.getCreatedAt());
    }
}
