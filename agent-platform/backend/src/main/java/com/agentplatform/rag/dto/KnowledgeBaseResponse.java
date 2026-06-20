package com.agentplatform.rag.dto;

import com.agentplatform.rag.KnowledgeBaseEntity;

import java.time.LocalDateTime;

public record KnowledgeBaseResponse(
        Long id,
        String name,
        String description,
        String embeddingModel,
        Integer chunkSize,
        Integer chunkOverlap,
        LocalDateTime createdAt
) {
    public static KnowledgeBaseResponse from(KnowledgeBaseEntity e) {
        return new KnowledgeBaseResponse(e.getId(), e.getName(), e.getDescription(),
                e.getEmbeddingModel(), e.getChunkSize(), e.getChunkOverlap(), e.getCreatedAt());
    }
}
