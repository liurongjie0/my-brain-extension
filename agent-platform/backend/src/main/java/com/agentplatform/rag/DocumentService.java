package com.agentplatform.rag;

import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.dto.DocumentResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final KnowledgeBaseService kbService;
    private final VectorStore vectorStore;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private DocumentProcessingService processingService;

    @Value("${rag.auto-process:true}")
    private boolean autoProcess;

    public DocumentService(DocumentRepository repository, KnowledgeBaseService kbService,
                           VectorStore vectorStore) {
        this.repository = repository;
        this.kbService = kbService;
        this.vectorStore = vectorStore;
    }

    // setter injection with @Lazy to break the cycle (processing depends on this service)
    @Autowired
    public void setProcessingService(@Lazy DocumentProcessingService processingService) {
        this.processingService = processingService;
    }

    public DocumentResponse upload(Long kbId, String filename, String fileType, String content) {
        kbService.getEntity(kbId); // validate exists
        DocumentEntity d = new DocumentEntity();
        d.setKbId(kbId);
        d.setFilename(filename);
        d.setFileType(fileType);
        d.setStatus("pending");
        d.setChunkCount(0);
        d.setRawText(content != null ? content : "");   // persist source so it survives restarts
        DocumentEntity saved = repository.save(d);
        Long id = saved.getId();
        if (autoProcess && processingService != null) {
            executor.submit(() -> processingService.process(id));
        }
        return DocumentResponse.from(saved);
    }

    /** Manually re-run processing for a document (e.g. retry a failed one). */
    public void reprocess(Long docId) {
        DocumentEntity doc = getEntity(docId);
        doc.setStatus("pending");
        repository.save(doc);
        if (processingService != null) {
            executor.submit(() -> processingService.process(docId));
        }
    }

    /** Re-enqueue documents left mid-processing by a previous run (crash / restart). */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverStuckDocuments() {
        if (!autoProcess || processingService == null) return;
        List<DocumentEntity> stuck = repository.findByStatusIn(List.of("pending", "processing"));
        if (stuck.isEmpty()) return;
        log.info("re-enqueueing {} document(s) stuck in pending/processing after restart", stuck.size());
        for (DocumentEntity d : stuck) {
            Long id = d.getId();
            executor.submit(() -> processingService.process(id));
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public List<DocumentResponse> list(Long kbId) {
        return repository.findByKbIdOrderByCreatedAtDesc(kbId).stream()
                .map(DocumentResponse::from).toList();
    }

    public DocumentEntity getEntity(Long docId) {
        return repository.findById(docId)
                .orElseThrow(() -> new BusinessException(40404, "document not found"));
    }

    public void delete(Long docId) {
        DocumentEntity doc = getEntity(docId);
        deleteVectors(docId);
        repository.delete(doc);
    }

    /** Remove this document's chunks from the vector store (best-effort). */
    public void deleteVectors(Long docId) {
        try {
            var b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("doc_id", String.valueOf(docId)).build());
        } catch (Exception ignored) {
            // index may not exist yet (no docs processed); nothing to delete
        }
    }
}
