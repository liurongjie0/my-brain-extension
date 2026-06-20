package com.agentplatform.agent;

import jakarta.persistence.*;

@Entity
@Table(name = "agent_knowledge_base")
@IdClass(AgentKbId.class)
public class AgentKnowledgeBaseEntity {

    @Id
    @Column(name = "agent_id")
    private Long agentId;

    @Id
    @Column(name = "kb_id")
    private Long kbId;

    public AgentKnowledgeBaseEntity() {}
    public AgentKnowledgeBaseEntity(Long agentId, Long kbId) {
        this.agentId = agentId;
        this.kbId = kbId;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
}
