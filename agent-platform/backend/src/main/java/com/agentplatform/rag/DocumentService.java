package com.agentplatform.rag;

import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.dto.DocumentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final KnowledgeBaseService kbService;
    private final ConcurrentHashMap<Long, String> rawTextById = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private DocumentProcessingService processingService;

    @Value("${rag.auto-process:true}")
    private boolean autoProcess;

    public DocumentService(DocumentRepository repository, KnowledgeBaseService kbService) {
        this.repository = repository;
        this.kbService = kbService;
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
        DocumentEntity saved = repository.save(d);
        Long id = saved.getId();
        rawTextById.put(id, content != null ? content : "");
        if (autoProcess && processingService != null) {
            executor.submit(() -> processingService.process(id));
        }
        return DocumentResponse.from(saved);
    }

    public String rawText(Long docId) {
        return rawTextById.get(docId);
    }

    public void clearRawText(Long docId) {
        rawTextById.remove(docId);
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
        repository.delete(getEntity(docId));
        rawTextById.remove(docId);
    }
}
