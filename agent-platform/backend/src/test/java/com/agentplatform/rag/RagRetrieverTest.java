package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.RetrieveResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrieverTest extends IntegrationTestBase {

    @Autowired RagRetriever retriever;
    @Autowired DocumentProcessingService processing;
    @Autowired DocumentService documentService;
    @Autowired KnowledgeBaseService kbService;

    @Test
    void retrieves_only_from_given_kb() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        var doc = documentService.upload(kbId, "x.txt", "txt", "KEYWORD_ALPHA 出现在这里。".repeat(20));
        processing.process(doc.id());

        List<RetrieveResult> results = retriever.retrieve(List.of(kbId), "KEYWORD_ALPHA", 3);
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r -> kbId.equals(r.kbId()));
    }
}
