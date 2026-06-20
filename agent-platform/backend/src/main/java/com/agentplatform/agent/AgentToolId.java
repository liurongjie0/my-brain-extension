package com.agentplatform.agent;

import java.io.Serializable;
import java.util.Objects;

public class AgentToolId implements Serializable {
    private Long agentId;
    private Long toolId;

    public AgentToolId() {}
    public AgentToolId(Long agentId, Long toolId) { this.agentId = agentId; this.toolId = toolId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentToolId that)) return false;
        return Objects.equals(agentId, that.agentId) && Objects.equals(toolId, that.toolId);
    }

    @Override
    public int hashCode() { return Objects.hash(agentId, toolId); }
}
