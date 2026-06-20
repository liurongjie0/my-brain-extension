package com.agentplatform.mcp.dto;

import jakarta.validation.constraints.NotBlank;

public record McpServerRequest(
        @NotBlank(message = "name 不能为空") String name,
        String transport,
        String url,
        String command,
        String args,
        Boolean enabled
) {}
