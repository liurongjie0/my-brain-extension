package com.agentplatform.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ModelConfigRequest(
        @NotBlank(message = "name 不能为空") String name,
        @NotBlank(message = "baseUrl 不能为空") String baseUrl,
        String apiKey,  // required on create; blank on update keeps the existing key
        @NotBlank(message = "model 不能为空") String model,
        Boolean enabled
) {}
