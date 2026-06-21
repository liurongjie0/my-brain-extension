package com.agentplatform.rag;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;
import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByKbIdOrderByCreatedAtDesc(Long kbId);

    /** docs stuck mid-processing — used to re-enqueue them after a restart */
    List<DocumentEntity> findByStatusIn(Collection<String> statuses);

    @Modifying
    @Transactional
    void deleteByKbId(Long kbId);
}
