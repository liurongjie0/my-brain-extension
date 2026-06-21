package com.agentplatform.tool;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.tool.dto.ToolRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ToolTestEndpointTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired ToolService toolService;

    @Test
    void test_endpoint_accepts_both_object_and_string_args() throws Exception {
        // points nowhere reachable; we only assert the endpoint doesn't 500 on arg shape
        var t = toolService.create(new ToolRequest("probe", "x", "POST",
                "http://example.invalid/none", null, "{\"type\":\"object\"}", true));

        // args as a raw JSON object (previously caused 500)
        mvc.perform(post("/api/admin/tools/" + t.id() + "/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"args\":{\"q\":\"x\"}}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0));

        // args as a JSON string (the frontend's format)
        mvc.perform(post("/api/admin/tools/" + t.id() + "/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"args\":\"{\\\"q\\\":\\\"x\\\"}\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0));
    }
}
