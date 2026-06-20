package com.agentplatform.rag;

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

    public DocumentProcessingService(DocumentService documentService,
                                     KnowledgeBaseService kbService,
                                     DocumentRepository documentRepository,
                                     VectorStore vectorStore) {
        this.documentService = documentService;
        this.kbService = kbService;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
    }

    public void process(Long docId) {
        DocumentEntity doc = documentService.getEntity(docId);
        doc.setStatus("processing");
        documentRepository.save(doc);
        try {
            KnowledgeBaseEntity kb = kbService.getEntity(doc.getKbId());
            String text = documentService.rawText(docId);
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
            doc.setStatus("failed");
            documentRepository.save(doc);
        } finally {
            documentService.clearRawText(docId);
        }
    }
}
