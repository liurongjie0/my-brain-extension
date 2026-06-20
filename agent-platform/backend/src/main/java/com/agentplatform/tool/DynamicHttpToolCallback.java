package com.agentplatform.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class DynamicHttpToolCallback implements ToolCallback {

    private final ToolEntity tool;
    private final HttpToolExecutor executor;

    public DynamicHttpToolCallback(ToolEntity tool, HttpToolExecutor executor) {
        this.tool = tool;
        this.executor = executor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        String schema = (tool.getParamsSchemaJson() != null && !tool.getParamsSchemaJson().isBlank())
                ? tool.getParamsSchemaJson() : "{\"type\":\"object\",\"properties\":{}}";
        return ToolDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription() != null ? tool.getDescription() : tool.getName())
                .inputSchema(schema)
                .build();
    }

    @Override
    public String call(String toolInput) {
        return executor.execute(tool, toolInput);
    }
}
