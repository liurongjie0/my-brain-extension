package com.agentplatform.rag;

import com.agentplatform.agent.AgentKnowledgeBaseRepository;
import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.KnowledgeBaseResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;
    private final DocumentRepository documentRepository;
    private final AgentKnowledgeBaseRepository agentKnowledgeBaseRepository;
    private final VectorStore vectorStore;

    public KnowledgeBaseService(KnowledgeBaseRepository repository,
                                DocumentRepository documentRepository,
                                AgentKnowledgeBaseRepository agentKnowledgeBaseRepository,
                                VectorStore vectorStore) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.agentKnowledgeBaseRepository = agentKnowledgeBaseRepository;
        this.vectorStore = vectorStore;
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

    /** Delete a KB and everything attached: vectors, document rows, and agent bindings. */
    @Transactional
    public void delete(Long id) {
        KnowledgeBaseEntity kb = getEntity(id);
        deleteVectors(id);
        documentRepository.deleteByKbId(id);
        agentKnowledgeBaseRepository.deleteByKbId(id);
        repository.delete(kb);
    }

    private void deleteVectors(Long kbId) {
        try {
            var b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("kb_id", String.valueOf(kbId)).build());
        } catch (Exception ignored) {
            // index may not exist yet; nothing to delete
        }
    }
}
