package com.agentplatform.agent;

import jakarta.persistence.*;

@Entity
@Table(name = "agent_tool")
@IdClass(AgentToolId.class)
public class AgentToolEntity {
    @Id
    @Column(name = "agent_id")
    private Long agentId;

    @Id
    @Column(name = "tool_id")
    private Long toolId;

    public AgentToolEntity() {}
    public AgentToolEntity(Long agentId, Long toolId) {
        this.agentId = agentId;
        this.toolId = toolId;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
}
