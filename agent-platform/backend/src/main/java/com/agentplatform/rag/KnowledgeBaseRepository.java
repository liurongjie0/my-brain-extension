package com.agentplatform.rag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {
}
