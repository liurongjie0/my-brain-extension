package com.agentplatform.model;

import com.agentplatform.common.ApiResponse;
import com.agentplatform.model.dto.ModelConfigRequest;
import com.agentplatform.model.dto.ModelConfigResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model-configs")
public class ModelConfigController {

    private final ModelConfigService service;

    public ModelConfigController(ModelConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ModelConfigResponse> create(@Valid @RequestBody ModelConfigRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping
    public ApiResponse<List<ModelConfigResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigResponse> update(@PathVariable Long id, @Valid @RequestBody ModelConfigRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
