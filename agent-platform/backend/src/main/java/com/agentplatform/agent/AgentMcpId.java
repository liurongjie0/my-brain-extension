package com.agentplatform.agent;

import java.io.Serializable;
import java.util.Objects;

public class AgentMcpId implements Serializable {
    private Long agentId;
    private Long mcpId;

    public AgentMcpId() {}
    public AgentMcpId(Long agentId, Long mcpId) { this.agentId = agentId; this.mcpId = mcpId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentMcpId that)) return false;
        return Objects.equals(agentId, that.agentId) && Objects.equals(mcpId, that.mcpId);
    }

    @Override
    public int hashCode() { return Objects.hash(agentId, mcpId); }
}
