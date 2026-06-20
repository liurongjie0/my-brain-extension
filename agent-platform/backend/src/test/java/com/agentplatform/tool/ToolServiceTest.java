package com.agentplatform.tool;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.tool.dto.ToolRequest;
import com.agentplatform.tool.dto.ToolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ToolServiceTest extends IntegrationTestBase {
    @Autowired ToolService service;

    @Test
    void create_applies_defaults() {
        ToolResponse r = service.create(new ToolRequest("天气查询", "查城市天气",
                null, "https://api.example.com/weather", null,
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}", null));
        assertThat(r.id()).isNotNull();
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.enabled()).isTrue();
    }
}
