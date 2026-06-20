package com.agentplatform.mcp;

import com.agentplatform.agent.AgentMcpRepository;
import com.agentplatform.common.BusinessException;
import com.agentplatform.mcp.dto.McpServerRequest;
import com.agentplatform.mcp.dto.McpServerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class McpServerService {

    private final McpServerRepository repository;
    private final AgentMcpRepository agentMcpRepository;
    private final McpClientManager clientManager;

    public McpServerService(McpServerRepository repository,
                            AgentMcpRepository agentMcpRepository,
                            McpClientManager clientManager) {
        this.repository = repository;
        this.agentMcpRepository = agentMcpRepository;
        this.clientManager = clientManager;
    }

    public McpServerResponse create(McpServerRequest req) {
        McpServerEntity e = new McpServerEntity();
        apply(e, req);
        return McpServerResponse.from(repository.save(e));
    }

    public List<McpServerResponse> listAll() {
        return repository.findAll().stream().map(McpServerResponse::from).toList();
    }

    public McpServerEntity getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(40407, "mcp server not found"));
    }

    public McpServerResponse update(Long id, McpServerRequest req) {
        McpServerEntity e = getEntity(id);
        apply(e, req);
        clientManager.evict(id);
        return McpServerResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        McpServerEntity e = getEntity(id);
        agentMcpRepository.deleteByMcpId(id);
        clientManager.evict(id);
        repository.delete(e);
    }

    /** Connect and return the tool names the server exposes (for the test button). */
    public List<String> testConnection(Long id) {
        clientManager.evict(id);
        return clientManager.listToolNames(getEntity(id));
    }

    private void apply(McpServerEntity e, McpServerRequest req) {
        e.setName(req.name());
        e.setTransport(req.transport() != null ? req.transport() : "sse");
        e.setUrl(req.url());
        e.setCommand(req.command());
        e.setArgs(req.args());
        e.setEnabled(req.enabled() != null ? req.enabled() : true);
    }
}
