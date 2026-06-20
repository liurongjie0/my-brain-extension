package com.agentplatform.mcp;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.mcp.dto.McpServerRequest;
import com.agentplatform.mcp.dto.McpServerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerServiceTest extends IntegrationTestBase {

    @Autowired McpServerService service;

    @Test
    void create_defaults_transport_sse_and_lists() {
        McpServerResponse r = service.create(new McpServerRequest(
                "my-mcp", null, "http://localhost:9999/sse", null, null, null));
        assertThat(r.id()).isNotNull();
        assertThat(r.transport()).isEqualTo("sse");
        assertThat(r.enabled()).isTrue();
        assertThat(service.listAll()).extracting(McpServerResponse::name).contains("my-mcp");

        service.delete(r.id());
        assertThat(service.listAll()).extracting(McpServerResponse::name).doesNotContain("my-mcp");
    }
}
