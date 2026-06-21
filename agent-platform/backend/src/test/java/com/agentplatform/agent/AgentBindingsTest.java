package com.agentplatform.agent;

import com.agentplatform.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AgentBindingsTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AgentRepository agents;

    @Test
    void set_then_read_back_bindings() throws Exception {
        AgentEntity a = new AgentEntity();
        a.setName("b"); a.setModel("m"); a.setAgentType("chat");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0); a.setEnabled(true);
        Long id = agents.save(a).getId();

        String body = om.writeValueAsString(Map.of("kbIds", List.of(), "toolIds", List.of(), "mcpIds", List.of()));
        mvc.perform(put("/api/admin/agents/" + id + "/bindings")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk());

        mvc.perform(get("/api/admin/agents/" + id + "/bindings"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.kbIds").isArray())
           .andExpect(jsonPath("$.data.toolIds").isArray())
           .andExpect(jsonPath("$.data.mcpIds").isArray());
    }
}
