package com.agentplatform.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeBaseRequest(
        @NotBlank(message = "name 不能为空") String name,
        String description,
        String embeddingModel,
        Integer chunkSize,
        Integer chunkOverlap
) {}
