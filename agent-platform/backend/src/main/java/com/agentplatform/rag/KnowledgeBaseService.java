package com.agentplatform.rag;

import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.KnowledgeBaseResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public KnowledgeBaseResponse create(KnowledgeBaseRequest req) {
        KnowledgeBaseEntity e = new KnowledgeBaseEntity();
        e.setName(req.name());
        e.setDescription(req.description());
        e.setEmbeddingModel(req.embeddingModel() != null ? req.embeddingModel() : "text-embedding-3-small");
        e.setChunkSize(req.chunkSize() != null ? req.chunkSize() : 800);
        e.setChunkOverlap(req.chunkOverlap() != null ? req.chunkOverlap() : 100);
        return KnowledgeBaseResponse.from(repository.save(e));
    }

    public List<KnowledgeBaseResponse> listAll() {
        return repository.findAll().stream().map(KnowledgeBaseResponse::from).toList();
    }

    public KnowledgeBaseEntity getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(40403, "knowledge base not found"));
    }

    public KnowledgeBaseResponse get(Long id) {
        return KnowledgeBaseResponse.from(getEntity(id));
    }

    public void delete(Long id) {
        repository.delete(getEntity(id));
    }
}
