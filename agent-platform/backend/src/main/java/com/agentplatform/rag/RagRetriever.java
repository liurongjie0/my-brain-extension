package com.agentplatform.rag;

import com.agentplatform.common.BusinessException;
import com.agentplatform.rag.dto.RetrieveResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagRetriever {

    private final VectorStore vectorStore;
    // RAG availability tracks the vector-store schema flag: when the model endpoint has no
    // embedding API (e.g. DeepSeek) the schema is left uninitialized and similarity search
    // would fail with an opaque 500. Defaults to that same flag, overridable via app.rag.enabled.
    private final boolean enabled;

    public RagRetriever(VectorStore vectorStore,
                        @Value("${app.rag.enabled:${app.vectorstore.initialize-schema:true}}") boolean enabled) {
        this.vectorStore = vectorStore;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<RetrieveResult> retrieve(List<Long> kbIds, String query, int topK) {
        if (!enabled) {
            throw new BusinessException(50010,
                    "向量检索未启用：当前模型端点未配置 embedding 接口（如 DeepSeek）");
        }
        List<RetrieveResult> all = new ArrayList<>();
        for (Long kbId : kbIds) {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression("kb_id == '" + kbId + "'")
                    .build());
            for (Document d : hits) {
                Long docId = parseLong(d.getMetadata().get("doc_id"));
                all.add(new RetrieveResult(d.getText(), kbId, docId, d.getScore()));
            }
        }
        return all;
    }

    private Long parseLong(Object v) {
        try {
            return v == null ? null : Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
