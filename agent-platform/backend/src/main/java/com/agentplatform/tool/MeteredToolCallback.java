package com.agentplatform.tool;

import com.agentplatform.metrics.ToolMetrics;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/** Wraps any ToolCallback to time it and record success/failure for monitoring. */
public class MeteredToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolMetrics metrics;
    private final String source;

    public MeteredToolCallback(ToolCallback delegate, ToolMetrics metrics, String source) {
        this.delegate = delegate;
        this.metrics = metrics;
        this.source = source;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        long start = System.nanoTime();
        boolean ok = false;
        try {
            String result = delegate.call(toolInput);
            ok = true;
            return result;
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            metrics.record(delegate.getToolDefinition().name(), source, ok, ms);
        }
    }
}
