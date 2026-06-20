package com.agentplatform.agent;

import com.agentplatform.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRepositoryTest extends IntegrationTestBase {

    @Autowired
    AgentRepository repository;

    @Test
    void saves_and_finds_enabled_agents() {
        AgentEntity a = new AgentEntity();
        a.setName("客服助手");
        a.setModel("gpt-4o-mini");
        a.setSystemPrompt("你是客服");
        a.setTemperature(0.5);
        a.setMaxTokens(1024);
        a.setTopP(1.0);
        a.setAgentType("chat");
        a.setEnabled(true);
        AgentEntity saved = repository.save(a);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        AgentEntity disabled = new AgentEntity();
        disabled.setName("停用的");
        disabled.setModel("gpt-4o-mini");
        disabled.setTemperature(0.7);
        disabled.setMaxTokens(2048);
        disabled.setTopP(1.0);
        disabled.setAgentType("chat");
        disabled.setEnabled(false);
        repository.save(disabled);

        List<AgentEntity> enabled = repository.findByEnabledTrue();
        assertThat(enabled).extracting(AgentEntity::getName).contains("客服助手").doesNotContain("停用的");
    }
}
