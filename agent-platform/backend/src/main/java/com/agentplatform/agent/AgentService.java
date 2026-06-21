package com.agentplatform.agent;

import com.agentplatform.agent.dto.AgentRequest;
import com.agentplatform.agent.dto.AgentResponse;
import com.agentplatform.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

    private final AgentRepository repository;
    private final AgentToolRepository toolRepo;
    private final AgentKnowledgeBaseRepository kbRepo;
    private final AgentMcpRepository mcpRepo;

    public AgentService(AgentRepository repository, AgentToolRepository toolRepo,
                        AgentKnowledgeBaseRepository kbRepo, AgentMcpRepository mcpRepo) {
        this.repository = repository;
        this.toolRepo = toolRepo;
        this.kbRepo = kbRepo;
        this.mcpRepo = mcpRepo;
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
        // attach binding counts so the chat picker can show capability chips (tools / KB / MCP)
        return repository.findByEnabledTrue().stream()
                .map(e -> AgentResponse.from(e,
                        toolRepo.countByAgentId(e.getId()),
                        kbRepo.countByAgentId(e.getId()),
                        mcpRepo.countByAgentId(e.getId())))
                .toList();
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
        e.setModelConfigId(req.modelConfigId());
        e.setEnabled(req.enabled() != null ? req.enabled() : true);
        e.setPlanEnabled(req.planEnabled() != null ? req.planEnabled() : false);
    }
}
