package com.agentplatform.model;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.model.dto.ModelConfigRequest;
import com.agentplatform.model.dto.ModelConfigResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ModelConfigServiceTest extends IntegrationTestBase {

    @Autowired ModelConfigService service;

    @Test
    void create_masks_api_key_and_defaults_enabled() {
        ModelConfigResponse r = service.create(new ModelConfigRequest(
                "deepseek", "https://api.deepseek.com", "sk-1234567890abcdef", "deepseek-chat", null));
        assertThat(r.id()).isNotNull();
        assertThat(r.enabled()).isTrue();
        assertThat(r.apiKeyMasked()).doesNotContain("567890");
        assertThat(r.apiKeyMasked()).startsWith("sk-1");

        assertThat(service.listAll()).extracting(ModelConfigResponse::name).contains("deepseek");
        // raw key retained internally for the factory
        assertThat(service.getEntity(r.id()).getApiKey()).isEqualTo("sk-1234567890abcdef");
    }
}
