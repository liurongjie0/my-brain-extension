package com.agentplatform.agent;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface AgentToolRepository extends JpaRepository<AgentToolEntity, AgentToolId> {
    List<AgentToolEntity> findByAgentId(Long agentId);

    long countByAgentId(Long agentId);

    @Modifying
    @Transactional
    void deleteByAgentId(Long agentId);
}
