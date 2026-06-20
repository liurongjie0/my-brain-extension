package com.agentplatform.chat;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AdminConversationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ConversationRepository conversations;
    @Autowired com.agentplatform.agent.AgentRepository agents;

    @Test
    void admin_lists_all_conversations() throws Exception {
        var a = new com.agentplatform.agent.AgentEntity();
        a.setName("x"); a.setModel("gpt-4o-mini"); a.setAgentType("chat");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0); a.setEnabled(true);
        Long agentId = agents.save(a).getId();
        ConversationEntity c = new ConversationEntity();
        c.setAgentId(agentId); c.setUserId("u-9"); c.setTitle("审计会话");
        conversations.save(c);

        mvc.perform(get("/api/admin/conversations"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data[?(@.title=='审计会话')]").exists());
    }
}
