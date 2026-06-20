package com.agentplatform.rag;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByKbIdOrderByCreatedAtDesc(Long kbId);

    @Modifying
    @Transactional
    void deleteByKbId(Long kbId);
}
