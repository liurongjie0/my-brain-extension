package com.agentplatform.rag.dto;

import com.agentplatform.rag.DocumentEntity;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id, Long kbId, String filename, String fileType,
        String status, Integer chunkCount, LocalDateTime createdAt
) {
    public static DocumentResponse from(DocumentEntity e) {
        return new DocumentResponse(e.getId(), e.getKbId(), e.getFilename(), e.getFileType(),
                e.getStatus(), e.getChunkCount(), e.getCreatedAt());
    }
}
