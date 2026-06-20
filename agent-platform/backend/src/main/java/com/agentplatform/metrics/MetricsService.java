package com.agentplatform.metrics;

import com.agentplatform.metrics.dto.MetricsDtos.AgentStat;
import com.agentplatform.metrics.dto.MetricsDtos.Dashboard;
import com.agentplatform.metrics.dto.MetricsDtos.Overview;
import com.agentplatform.metrics.dto.MetricsDtos.ToolStat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** Read-only aggregation for the monitoring dashboard, straight from the tables. */
@Service
public class MetricsService {

    private final JdbcTemplate jdbc;

    public MetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Dashboard dashboard() {
        return new Dashboard(overview(), toolStats(), agentStats());
    }

    public Overview overview() {
        long agents = count("SELECT COUNT(*) FROM agent");
        long enabled = count("SELECT COUNT(*) FROM agent WHERE enabled = 1");
        long conversations = count("SELECT COUNT(*) FROM conversation");
        long messages = count("SELECT COUNT(*) FROM message");
        long toolCalls = count("SELECT COUNT(*) FROM tool_call_log");
        long toolSuccess = count("SELECT COUNT(*) FROM tool_call_log WHERE success = 1");
        double rate = toolCalls == 0 ? 0.0 : round(toolSuccess * 100.0 / toolCalls);
        return new Overview(agents, enabled, conversations, messages, toolCalls, rate);
    }

    public List<ToolStat> toolStats() {
        return jdbc.query(
                "SELECT tool_name, COUNT(*) AS total, SUM(success) AS ok " +
                        "FROM tool_call_log GROUP BY tool_name ORDER BY total DESC",
                (rs, i) -> {
                    long total = rs.getLong("total");
                    long ok = rs.getLong("ok");
                    return new ToolStat(rs.getString("tool_name"), total, ok,
                            total == 0 ? 0.0 : round(ok * 100.0 / total));
                });
    }

    public List<AgentStat> agentStats() {
        return jdbc.query(
                "SELECT a.id AS id, a.name AS name, " +
                        "COUNT(DISTINCT c.id) AS conversations, " +
                        "SUM(CASE WHEN m.role = 'user' THEN 1 ELSE 0 END) AS calls " +
                        "FROM agent a " +
                        "LEFT JOIN conversation c ON c.agent_id = a.id " +
                        "LEFT JOIN message m ON m.conversation_id = c.id " +
                        "GROUP BY a.id, a.name ORDER BY calls DESC",
                (rs, i) -> new AgentStat(rs.getLong("id"), rs.getString("name"),
                        rs.getLong("conversations"), rs.getLong("calls")));
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n != null ? n : 0L;
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
