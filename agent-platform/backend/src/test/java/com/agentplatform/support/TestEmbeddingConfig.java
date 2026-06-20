package com.agentplatform.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }
}
