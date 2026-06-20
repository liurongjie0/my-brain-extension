package com.agentplatform.metrics;

import org.springframework.stereotype.Component;

/** Records each tool invocation for the monitoring dashboard. */
@Component
public class ToolMetrics {

    private final ToolCallLogRepository repository;

    public ToolMetrics(ToolCallLogRepository repository) {
        this.repository = repository;
    }

    public void record(String toolName, String source, boolean success, long durationMs) {
        ToolCallLogEntity log = new ToolCallLogEntity();
        log.setToolName(toolName);
        log.setSource(source);
        log.setSuccess(success);
        log.setDurationMs(durationMs);
        repository.save(log);
    }
}
