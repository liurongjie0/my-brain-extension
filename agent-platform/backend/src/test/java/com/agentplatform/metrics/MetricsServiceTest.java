package com.agentplatform.metrics;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentRepository;
import com.agentplatform.chat.ConversationEntity;
import com.agentplatform.chat.ConversationRepository;
import com.agentplatform.chat.MessageEntity;
import com.agentplatform.chat.MessageRepository;
import com.agentplatform.metrics.dto.MetricsDtos.AgentStat;
import com.agentplatform.metrics.dto.MetricsDtos.Dashboard;
import com.agentplatform.metrics.dto.MetricsDtos.ToolStat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsServiceTest extends IntegrationTestBase {

    @Autowired MetricsService metricsService;
    @Autowired ToolMetrics toolMetrics;
    @Autowired AgentRepository agents;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;

    @Test
    void aggregates_tool_success_rate_and_agent_calls() {
        // tool logs: weather 2/3 ok, calc 1/1 ok
        toolMetrics.record("weather", "http", true, 10);
        toolMetrics.record("weather", "http", true, 12);
        toolMetrics.record("weather", "http", false, 8);
        toolMetrics.record("calc", "http", true, 5);

        // an agent with one conversation and two user turns
        AgentEntity a = new AgentEntity();
        a.setName("metrics-agent"); a.setModel("deepseek-chat"); a.setAgentType("chat");
        a.setTemperature(0.7); a.setMaxTokens(256); a.setTopP(1.0); a.setEnabled(true);
        Long agentId = agents.save(a).getId();
        ConversationEntity c = new ConversationEntity();
        c.setAgentId(agentId); c.setUserId("u"); c.setTitle("t");
        Long convId = conversations.save(c).getId();
        for (String role : new String[]{"user", "assistant", "user", "assistant"}) {
            MessageEntity m = new MessageEntity();
            m.setConversationId(convId); m.setRole(role); m.setContent("x");
            messages.save(m);
        }

        Dashboard d = metricsService.dashboard();

        assertThat(d.overview().toolCalls()).isGreaterThanOrEqualTo(4);
        ToolStat weather = d.tools().stream().filter(t -> t.toolName().equals("weather")).findFirst().orElseThrow();
        assertThat(weather.total()).isEqualTo(3);
        assertThat(weather.success()).isEqualTo(2);
        assertThat(weather.successRate()).isEqualTo(66.7);

        AgentStat stat = d.agents().stream().filter(s -> s.agentId().equals(agentId)).findFirst().orElseThrow();
        assertThat(stat.conversations()).isEqualTo(1);
        assertThat(stat.calls()).isEqualTo(2);
    }
}
