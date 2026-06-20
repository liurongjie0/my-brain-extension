package com.agentplatform.agent;

import com.agentplatform.agent.dto.AgentRequest;
import com.agentplatform.agent.dto.AgentResponse;
import com.agentplatform.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

    private final AgentRepository repository;

    public AgentService(AgentRepository repository) {
        this.repository = repository;
    }

    public AgentResponse create(AgentRequest req) {
        AgentEntity e = new AgentEntity();
        apply(e, req);
        return AgentResponse.from(repository.save(e));
    }

    public List<AgentResponse> listAll() {
        return repository.findAll().stream().map(AgentResponse::from).toList();
    }

    public List<AgentResponse> listEnabled() {
        return repository.findByEnabledTrue().stream().map(AgentResponse::from).toList();
    }

    public AgentResponse get(Long id) {
        return AgentResponse.from(find(id));
    }

    public AgentResponse update(Long id, AgentRequest req) {
        AgentEntity e = find(id);
        apply(e, req);
        return AgentResponse.from(repository.save(e));
    }

    public void delete(Long id) {
        repository.delete(find(id));
    }

    private AgentEntity find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(40401, "agent not found"));
    }

    private void apply(AgentEntity e, AgentRequest req) {
        e.setName(req.name());
        e.setDescription(req.description());
        e.setAvatar(req.avatar());
        e.setSystemPrompt(req.systemPrompt());
        e.setModel(req.model());
        e.setTemperature(req.temperature() != null ? req.temperature() : 0.7);
        e.setTopP(req.topP() != null ? req.topP() : 1.0);
        e.setMaxTokens(req.maxTokens() != null ? req.maxTokens() : 2048);
        e.setAgentType(req.agentType() != null ? req.agentType() : "chat");
        e.setEnabled(req.enabled() != null ? req.enabled() : true);
    }
}
