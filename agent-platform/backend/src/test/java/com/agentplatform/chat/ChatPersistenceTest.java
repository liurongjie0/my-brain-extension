package com.agentplatform.chat;

import com.agentplatform.IntegrationTestBase;
import com.agentplatform.agent.AgentEntity;
import com.agentplatform.agent.AgentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPersistenceTest extends IntegrationTestBase {

    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired AgentRepository agents;

    @Test
    void persists_conversation_and_ordered_messages() {
        AgentEntity agent = new AgentEntity();
        agent.setName("t"); agent.setModel("gpt-4o-mini"); agent.setAgentType("chat");
        agent.setTemperature(0.7); agent.setMaxTokens(256); agent.setTopP(1.0);
        agent.setEnabled(true);
        Long agentId = agents.save(agent).getId();

        ConversationEntity c = new ConversationEntity();
        c.setAgentId(agentId);
        c.setUserId("u-1");
        c.setTitle("第一次对话");
        ConversationEntity saved = conversations.save(c);
        assertThat(saved.getId()).isNotNull();

        MessageEntity m1 = new MessageEntity();
        m1.setConversationId(saved.getId());
        m1.setRole("user");
        m1.setContent("你好");
        messages.save(m1);

        MessageEntity m2 = new MessageEntity();
        m2.setConversationId(saved.getId());
        m2.setRole("assistant");
        m2.setContent("你好，有什么可以帮你");
        messages.save(m2);

        List<MessageEntity> ordered = messages.findByConversationIdOrderByCreatedAtAsc(saved.getId());
        assertThat(ordered).extracting(MessageEntity::getRole).containsExactly("user", "assistant");

        List<ConversationEntity> mine = conversations.findByUserIdOrderByUpdatedAtDesc("u-1");
        assertThat(mine).extracting(ConversationEntity::getId).contains(saved.getId());
    }
}
