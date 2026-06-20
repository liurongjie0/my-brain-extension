package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreSmokeTest extends IntegrationTestBase {

    @Autowired VectorStore vectorStore;

    @Test
    void stores_and_filters_by_kb_id() {
        vectorStore.add(List.of(
                new Document("apple apple apple", Map.of("kb_id", "1001", "doc_id", "d1")),
                new Document("banana banana banana", Map.of("kb_id", "2002", "doc_id", "d2"))
        ));

        List<Document> kb1 = vectorStore.similaritySearch(SearchRequest.builder()
                .query("apple apple apple").topK(5)
                .filterExpression("kb_id == '1001'").build());

        assertThat(kb1).isNotEmpty();
        assertThat(kb1).allMatch(d -> "1001".equals(d.getMetadata().get("kb_id")));
    }
}
