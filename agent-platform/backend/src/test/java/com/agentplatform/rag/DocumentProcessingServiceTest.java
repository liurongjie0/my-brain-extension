package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.DocumentResponse;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentProcessingServiceTest extends IntegrationTestBase {

    @Autowired DocumentProcessingService processing;
    @Autowired DocumentService documentService;
    @Autowired KnowledgeBaseService kbService;
    @Autowired VectorStore vectorStore;

    @Test
    void process_chunks_embeds_and_marks_done() {
        Long kbId = kbService.create(new KnowledgeBaseRequest("kb", null, null, null, null)).id();
        String text = "Spring AI 是一个用于构建 AI 应用的框架。".repeat(50);
        DocumentResponse doc = documentService.upload(kbId, "g.txt", "txt", text);

        processing.process(doc.id());

        DocumentEntity after = documentService.getEntity(doc.id());
        assertThat(after.getStatus()).isEqualTo("done");
        assertThat(after.getChunkCount()).isGreaterThan(0);

        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("Spring AI 框架").topK(3)
                .filterExpression("kb_id == '" + kbId + "'").build());
        assertThat(hits).isNotEmpty();
        assertThat(hits).allMatch(d -> String.valueOf(kbId).equals(d.getMetadata().get("kb_id")));
    }
}
