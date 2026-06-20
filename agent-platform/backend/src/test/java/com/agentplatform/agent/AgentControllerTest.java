package com.agentplatform.agent;

import com.agentplatform.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AgentControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void create_then_list_and_public_filters_disabled() throws Exception {
        String body = om.writeValueAsString(new java.util.HashMap<>() {{
            put("name", "公开助手");
            put("model", "gpt-4o-mini");
            put("enabled", true);
        }});
        mvc.perform(post("/api/admin/agents").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.id").isNumber())
           .andExpect(jsonPath("$.data.name").value("公开助手"));

        mvc.perform(get("/api/agents"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[?(@.name=='公开助手')]").exists());
    }
}
