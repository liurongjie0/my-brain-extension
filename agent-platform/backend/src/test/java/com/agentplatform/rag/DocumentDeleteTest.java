package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.RetrieveResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDeleteTest extends IntegrationTestBase {

    @Autowired DocumentService documentService;
    @Autowired DocumentProcessingService processing;
    @Autowired KnowledgeBaseService kbService;
    @Autowired RagRetriever retriever;

    @Test
    void deleting_document_removes_its_vectors() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        var doc = documentService.upload(kbId, "d.txt", "txt", "DELETE_ME_TOKEN 在此重复出现。".repeat(20));
        processing.process(doc.id());

        List<RetrieveResult> before = retriever.retrieve(List.of(kbId), "DELETE_ME_TOKEN", 5);
        assertThat(before).isNotEmpty();

        documentService.delete(doc.id());

        List<RetrieveResult> after = retriever.retrieve(List.of(kbId), "DELETE_ME_TOKEN", 5);
        assertThat(after).isEmpty();
    }
}
