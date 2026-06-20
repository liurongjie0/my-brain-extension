package com.agentplatform.agent;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface AgentMcpRepository extends JpaRepository<AgentMcpEntity, AgentMcpId> {
    List<AgentMcpEntity> findByAgentId(Long agentId);

    @Modifying
    @Transactional
    void deleteByAgentId(Long agentId);

    @Modifying
    @Transactional
    void deleteByMcpId(Long mcpId);
}
