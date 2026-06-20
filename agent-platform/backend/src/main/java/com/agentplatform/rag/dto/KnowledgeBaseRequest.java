package com.agentplatform.rag.dto;

public record KnowledgeBaseRequest(
        String name,
        String description,
        String embeddingModel,
        Integer chunkSize,
        Integer chunkOverlap
) {}
