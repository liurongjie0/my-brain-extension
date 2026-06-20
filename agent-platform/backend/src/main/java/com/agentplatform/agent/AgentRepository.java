package com.agentplatform.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<AgentEntity, Long> {
    List<AgentEntity> findByEnabledTrue();
}
