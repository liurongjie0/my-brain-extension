package com.agentplatform.tool;

import com.agentplatform.common.BusinessException;
import com.agentplatform.tool.dto.ToolRequest;
import com.agentplatform.tool.dto.ToolResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToolService {
    private final ToolRepository repository;

    public ToolService(ToolRepository repository) {
        this.repository = repository;
    }

    public ToolResponse create(ToolRequest req) {
        ToolEntity e = new ToolEntity();
        apply(e, req);
        return ToolResponse.from(repository.save(e));
    }

    public List<ToolResponse> listAll() {
        return repository.findAll().stream().map(ToolResponse::from).toList();
    }

    public ToolEntity getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(40405, "tool not found"));
    }

    public ToolResponse get(Long id) {
        return ToolResponse.from(getEntity(id));
    }

    public ToolResponse update(Long id, ToolRequest req) {
        ToolEntity e = getEntity(id);
        apply(e, req);
        return ToolResponse.from(repository.save(e));
    }

    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private void apply(ToolEntity e, ToolRequest req) {
        e.setName(req.name());
        e.setDescription(req.description());
        e.setMethod(req.method() != null ? req.method() : "POST");
        e.setUrl(req.url());
        e.setHeadersJson(req.headersJson());
        e.setParamsSchemaJson(req.paramsSchemaJson());
        e.setEnabled(req.enabled() != null ? req.enabled() : true);
    }
}
