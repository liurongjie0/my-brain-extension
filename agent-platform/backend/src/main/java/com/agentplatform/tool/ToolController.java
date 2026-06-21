package com.agentplatform.tool;

import com.agentplatform.common.ApiResponse;
import com.agentplatform.tool.dto.ToolRequest;
import com.agentplatform.tool.dto.ToolResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tools")
public class ToolController {
    private final ToolService service;
    private final HttpToolExecutor executor;

    public ToolController(ToolService service, HttpToolExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @PostMapping
    public ApiResponse<ToolResponse> create(@Valid @RequestBody ToolRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping
    public ApiResponse<List<ToolResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ToolResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ToolResponse> update(@PathVariable Long id, @Valid @RequestBody ToolRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    // args tolerates both a JSON string ("{\"q\":\"x\"}") and a raw JSON object ({"q":"x"})
    public record TestRequest(JsonNode args) {}

    @PostMapping("/{id}/test")
    public ApiResponse<String> test(@PathVariable Long id, @RequestBody TestRequest req) {
        return ApiResponse.ok(executor.execute(service.getEntity(id), toArgsJson(req.args())).body());
    }

    private String toArgsJson(JsonNode args) {
        if (args == null || args.isNull()) return "{}";
        return args.isTextual() ? args.asText() : args.toString();
    }
}
