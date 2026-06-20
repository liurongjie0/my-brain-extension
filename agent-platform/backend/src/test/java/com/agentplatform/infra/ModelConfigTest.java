package com.agentplatform.infra;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ModelConfigTest extends IntegrationTestBase {

    @Autowired(required = false)
    ChatModel chatModel;

    @Autowired(required = false)
    EmbeddingModel embeddingModel;

    @Test
    void openai_compatible_models_are_wired() {
        assertThat(chatModel).as("ChatModel bean").isNotNull();
        assertThat(embeddingModel).as("EmbeddingModel bean").isNotNull();
    }
}
