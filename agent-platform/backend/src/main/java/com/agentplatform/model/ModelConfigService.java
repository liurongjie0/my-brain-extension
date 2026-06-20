package com.agentplatform.model;

import com.agentplatform.common.BusinessException;
import com.agentplatform.model.dto.ModelConfigRequest;
import com.agentplatform.model.dto.ModelConfigResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelConfigService {

    private final ModelConfigRepository repository;

    public ModelConfigService(ModelConfigRepository repository) {
        this.repository = repository;
    }

    public ModelConfigResponse create(ModelConfigRequest req) {
        if (req.apiKey() == null || req.apiKey().isBlank()) {
            throw new BusinessException(40000, "apiKey 不能为空");
        }
        ModelConfigEntity e = new ModelConfigEntity();
        apply(e, req);
        return ModelConfigResponse.from(repository.save(e));
    }

    public List<ModelConfigResponse> listAll() {
        return repository.findAll().stream().map(ModelConfigResponse::from).toList();
    }

    public ModelConfigEntity getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(40406, "model config not found"));
    }

    public ModelConfigResponse update(Long id, ModelConfigRequest req) {
        ModelConfigEntity e = getEntity(id);
        apply(e, req);
        return ModelConfigResponse.from(repository.save(e));
    }

    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private void apply(ModelConfigEntity e, ModelConfigRequest req) {
        e.setName(req.name());
        e.setBaseUrl(req.baseUrl());
        // blank apiKey on update => keep the existing key
        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            e.setApiKey(req.apiKey());
        }
        e.setModel(req.model());
        e.setEnabled(req.enabled() != null ? req.enabled() : true);
    }
}
