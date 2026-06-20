package com.agentplatform.tool;

import com.agentplatform.metrics.ToolMetrics;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class DynamicHttpToolCallback implements ToolCallback {

    private final ToolEntity tool;
    private final HttpToolExecutor executor;
    private final ToolMetrics metrics;

    public DynamicHttpToolCallback(ToolEntity tool, HttpToolExecutor executor, ToolMetrics metrics) {
        this.tool = tool;
        this.executor = executor;
        this.metrics = metrics;
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
        long start = System.nanoTime();
        HttpToolExecutor.Outcome outcome = executor.execute(tool, toolInput);
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (metrics != null) {
            metrics.record(tool.getName(), "http", outcome.ok(), ms);
        }
        return outcome.body();
    }
}
