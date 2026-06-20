package com.agentplatform.agent;

import jakarta.persistence.*;

@Entity
@Table(name = "agent_mcp")
@IdClass(AgentMcpId.class)
public class AgentMcpEntity {
    @Id
    @Column(name = "agent_id")
    private Long agentId;

    @Id
    @Column(name = "mcp_id")
    private Long mcpId;

    public AgentMcpEntity() {}
    public AgentMcpEntity(Long agentId, Long mcpId) {
        this.agentId = agentId;
        this.mcpId = mcpId;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getMcpId() { return mcpId; }
    public void setMcpId(Long mcpId) { this.mcpId = mcpId; }
}
