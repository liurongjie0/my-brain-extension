package com.agentplatform.mcp;

import com.agentplatform.common.ApiResponse;
import com.agentplatform.mcp.dto.McpServerRequest;
import com.agentplatform.mcp.dto.McpServerResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mcp-servers")
public class McpController {

    private final McpServerService service;

    public McpController(McpServerService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<McpServerResponse> create(@Valid @RequestBody McpServerRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping
    public ApiResponse<List<McpServerResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @PutMapping("/{id}")
    public ApiResponse<McpServerResponse> update(@PathVariable Long id, @Valid @RequestBody McpServerRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    /** Connect to the server and list its tools — surfaces errors as a business error. */
    @PostMapping("/{id}/test")
    public ApiResponse<List<String>> test(@PathVariable Long id) {
        try {
            return ApiResponse.ok(service.testConnection(id));
        } catch (Exception e) {
            return ApiResponse.error(50001, "连接失败: " + e.getMessage());
        }
    }
}
