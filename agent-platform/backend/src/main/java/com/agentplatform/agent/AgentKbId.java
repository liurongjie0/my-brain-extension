package com.agentplatform.agent;

import java.io.Serializable;
import java.util.Objects;

public class AgentKbId implements Serializable {
    private Long agentId;
    private Long kbId;

    public AgentKbId() {}
    public AgentKbId(Long agentId, Long kbId) { this.agentId = agentId; this.kbId = kbId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentKbId that)) return false;
        return Objects.equals(agentId, that.agentId) && Objects.equals(kbId, that.kbId);
    }

    @Override
    public int hashCode() { return Objects.hash(agentId, kbId); }
}
