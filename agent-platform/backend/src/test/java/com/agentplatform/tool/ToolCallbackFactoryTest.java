package com.agentplatform.tool;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.tool.dto.ToolRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallbackFactoryTest extends IntegrationTestBase {
    @Autowired ToolService toolService;
    @Autowired ToolCallbackFactory factory;

    @Test
    void builds_callback_with_definition() {
        var t = toolService.create(new ToolRequest("weather", "查天气", "POST",
                "http://localhost:9/none", null,
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}", true));
        List<ToolCallback> callbacks = factory.build(List.of(t.id()));
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.get(0).getToolDefinition().name()).isEqualTo("weather");
        assertThat(callbacks.get(0).getToolDefinition().description()).isEqualTo("查天气");
    }
}
