package com.agentplatform.rag;

import com.agentplatform.rag.dto.RetrieveResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagRetriever {

    private final VectorStore vectorStore;

    public RagRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<RetrieveResult> retrieve(List<Long> kbIds, String query, int topK) {
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
