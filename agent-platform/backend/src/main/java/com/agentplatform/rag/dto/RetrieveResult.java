package com.agentplatform.rag.dto;

public record RetrieveResult(String content, Long kbId, Long docId, Double score) {}
