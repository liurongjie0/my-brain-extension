package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.DocumentResponse;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentServiceTest extends IntegrationTestBase {

    @Autowired DocumentService documentService;
    @Autowired KnowledgeBaseService kbService;

    @Test
    void upload_persists_pending_document_and_keeps_text() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        DocumentResponse doc = documentService.upload(kbId, "a.txt", "txt", "hello world content");
        assertThat(doc.id()).isNotNull();
        assertThat(doc.status()).isEqualTo("pending");
        assertThat(documentService.rawText(doc.id())).isEqualTo("hello world content");
        assertThat(documentService.list(kbId)).extracting(DocumentResponse::id).contains(doc.id());
    }
}
