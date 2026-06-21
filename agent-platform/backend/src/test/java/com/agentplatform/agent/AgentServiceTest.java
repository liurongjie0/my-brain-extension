package com.agentplatform.agent;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.dto.AgentRequest;
import com.agentplatform.agent.dto.AgentResponse;
import com.agentplatform.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServiceTest extends IntegrationTestBase {

    @Autowired
    AgentService service;

    @Test
    void create_applies_defaults() {
        AgentRequest req = new AgentRequest("助手", null, null, "你好", "gpt-4o-mini",
                null, null, null, null, null, null, null);
        AgentResponse r = service.create(req);

        assertThat(r.id()).isNotNull();
        assertThat(r.temperature()).isEqualTo(0.7);
        assertThat(r.maxTokens()).isEqualTo(2048);
        assertThat(r.topP()).isEqualTo(1.0);
        assertThat(r.agentType()).isEqualTo("chat");
        assertThat(r.enabled()).isTrue();
        assertThat(r.planEnabled()).isFalse();
    }

    @Test
    void update_changes_fields() {
        AgentResponse created = service.create(new AgentRequest("a", null, null, null,
                "gpt-4o-mini", null, null, null, null, null, null, null));
        AgentResponse updated = service.update(created.id(), new AgentRequest("b", "desc", null,
                "sys", "gpt-4o", 0.2, 0.9, 512, "react", null, false, true));

        assertThat(updated.name()).isEqualTo("b");
        assertThat(updated.model()).isEqualTo("gpt-4o");
        assertThat(updated.agentType()).isEqualTo("react");
        assertThat(updated.planEnabled()).isTrue();
        assertThat(updated.enabled()).isFalse();
    }

    @Test
    void get_missing_throws() {
        assertThatThrownBy(() -> service.get(999999L))
                .isInstanceOf(BusinessException.class);
    }
}
