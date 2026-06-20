package com.agentplatform.metrics;

import com.agentplatform.common.ApiResponse;
import com.agentplatform.metrics.dto.MetricsDtos.Dashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/metrics")
public class MetricsController {

    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.ok(service.dashboard());
    }
}
