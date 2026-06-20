package com.agentplatform.agent;

import com.agentplatform.agent.dto.AgentRequest;
import com.agentplatform.agent.dto.AgentResponse;
import com.agentplatform.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AgentController {

    private final AgentService service;
    private final AgentKnowledgeBaseRepository agentKnowledgeBaseRepository;

    public AgentController(AgentService service,
                          AgentKnowledgeBaseRepository agentKnowledgeBaseRepository) {
        this.service = service;
        this.agentKnowledgeBaseRepository = agentKnowledgeBaseRepository;
    }

    @PostMapping("/api/admin/agents")
    public ApiResponse<AgentResponse> create(@RequestBody AgentRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping("/api/admin/agents")
    public ApiResponse<List<AgentResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/api/admin/agents/{id}")
    public ApiResponse<AgentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/api/admin/agents/{id}")
    public ApiResponse<AgentResponse> update(@PathVariable Long id, @RequestBody AgentRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/api/admin/agents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/agents")
    public ApiResponse<List<AgentResponse>> listEnabled() {
        return ApiResponse.ok(service.listEnabled());
    }

    public record BindingsRequest(List<Long> kbIds) {}

    @PutMapping("/api/admin/agents/{id}/bindings")
    public ApiResponse<Void> bindings(@PathVariable Long id, @RequestBody BindingsRequest req) {
        service.get(id); // validate agent exists
        agentKnowledgeBaseRepository.deleteByAgentId(id);
        if (req.kbIds() != null) {
            for (Long kbId : req.kbIds()) {
                agentKnowledgeBaseRepository.save(new AgentKnowledgeBaseEntity(id, kbId));
            }
        }
        return ApiResponse.ok(null);
    }
}
