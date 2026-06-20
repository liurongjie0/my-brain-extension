package com.agentplatform.metrics.dto;

import java.util.List;

/** DTOs for the monitoring dashboard. */
public final class MetricsDtos {

    public record Overview(
            long agents,
            long enabledAgents,
            long conversations,
            long messages,
            long toolCalls,
            double toolSuccessRate
    ) {}

    public record ToolStat(String toolName, long total, long success, double successRate) {}

    public record AgentStat(Long agentId, String agentName, long conversations, long calls) {}

    public record Dashboard(Overview overview, List<ToolStat> tools, List<AgentStat> agents) {}

    private MetricsDtos() {}
}
