package com.agentplatform.rag;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.rag.dto.KnowledgeBaseRequest;
import com.agentplatform.rag.dto.KnowledgeBaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseServiceTest extends IntegrationTestBase {

    @Autowired KnowledgeBaseService service;

    @Test
    void create_applies_defaults() {
        KnowledgeBaseResponse r = service.create(
                new KnowledgeBaseRequest("产品手册", null, null, null, null));
        assertThat(r.id()).isNotNull();
        assertThat(r.embeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(r.chunkSize()).isEqualTo(800);
        assertThat(r.chunkOverlap()).isEqualTo(100);
    }
}
