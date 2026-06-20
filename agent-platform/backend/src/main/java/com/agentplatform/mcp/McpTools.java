package com.agentplatform.mcp;

import com.agentplatform.agent.AgentMcpEntity;
import com.agentplatform.agent.AgentMcpRepository;
import com.agentplatform.metrics.ToolMetrics;
import com.agentplatform.tool.MeteredToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Collects MCP tool callbacks for an agent's bound MCP servers (failures are skipped). */
@Component
public class McpTools {

    private static final Logger log = LoggerFactory.getLogger(McpTools.class);

    private final AgentMcpRepository agentMcp;
    private final McpServerRepository servers;
    private final McpClientManager clientManager;
    private final ToolMetrics metrics;

    public McpTools(AgentMcpRepository agentMcp, McpServerRepository servers,
                    McpClientManager clientManager, ToolMetrics metrics) {
        this.agentMcp = agentMcp;
        this.servers = servers;
        this.clientManager = clientManager;
        this.metrics = metrics;
    }

    public List<ToolCallback> forAgent(Long agentId) {
        List<Long> ids = agentMcp.findByAgentId(agentId).stream()
                .map(AgentMcpEntity::getMcpId).toList();
        List<ToolCallback> out = new ArrayList<>();
        for (McpServerEntity s : servers.findAllById(ids)) {
            if (!Boolean.TRUE.equals(s.getEnabled())) continue;
            try {
                for (ToolCallback tc : clientManager.toolCallbacks(s)) {
                    out.add(new MeteredToolCallback(tc, metrics, "mcp"));
                }
            } catch (Exception e) {
                // an unreachable MCP server must not break the chat turn
                log.warn("MCP server {} unavailable: {}", s.getName(), e.getMessage());
            }
        }
        return out;
    }
}
