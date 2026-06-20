package com.agentplatform.tool.dto;

public record ToolRequest(String name, String description, String method, String url,
                          String headersJson, String paramsSchemaJson, Boolean enabled) {}
