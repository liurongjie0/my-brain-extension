package com.agentplatform.tool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolRepository extends JpaRepository<ToolEntity, Long> {
    List<ToolEntity> findByEnabledTrue();
}
