package com.agentplatform.rag;

import com.agentplatform.rag.RagRetriever;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentProcessingService {

    private final DocumentService documentService;
    private final KnowledgeBaseService kbService;
    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final RagRetriever ragRetriever;

    public DocumentProcessingService(DocumentService documentService,
                                     KnowledgeBaseService kbService,
                                     DocumentRepository documentRepository,
                                     VectorStore vectorStore,
                                     RagRetriever ragRetriever) {
        this.documentService = documentService;
        this.kbService = kbService;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.ragRetriever = ragRetriever;
    }

    public void process(Long docId) {
        DocumentEntity doc = documentService.getEntity(docId);
        // RAG disabled (no embedding endpoint): don't attempt embedding, mark as skipped so the
        // UI shows a clear "未启用" state instead of a misleading "失败".
        if (!ragRetriever.isEnabled()) {
            doc.setStatus("skipped");
            doc.setChunkCount(0);
            documentRepository.save(doc);
            return;
        }
        doc.setStatus("processing");
        documentRepository.save(doc);
        try {
            // clear any chunks from a previous/partial run, so reprocessing is idempotent
            documentService.deleteVectors(docId);

            KnowledgeBaseEntity kb = kbService.getEntity(doc.getKbId());
            String text = doc.getRawText();   // source persisted in DB (survives restarts)
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("empty document text");
            }

            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(kb.getChunkSize())
                    .build();
            Document raw = new Document(text);
            List<Document> chunks = splitter.apply(List.of(raw));

            List<Document> toStore = new ArrayList<>();
            for (Document c : chunks) {
                toStore.add(new Document(c.getText(), Map.of(
                        "kb_id", String.valueOf(doc.getKbId()),
                        "doc_id", String.valueOf(docId))));
            }
            vectorStore.add(toStore);

            doc.setChunkCount(toStore.size());
            doc.setStatus("done");
            documentRepository.save(doc);
        } catch (Exception e) {
            // a failed run must not leave half-written chunks behind that later pollute retrieval
            documentService.deleteVectors(docId);
            doc.setChunkCount(0);
            doc.setStatus("failed");
            documentRepository.save(doc);
        }
    }
}
