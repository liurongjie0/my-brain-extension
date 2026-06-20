package com.agentplatform.tool.dto;

import jakarta.validation.constraints.NotBlank;

public record ToolRequest(@NotBlank(message = "name 不能为空") String name,
                          String description, String method,
                          @NotBlank(message = "url 不能为空") String url,
                          String headersJson, String paramsSchemaJson, Boolean enabled) {}
