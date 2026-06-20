package com.agentplatform.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, network-free embedding for tests: 8-dim vector derived from the
 * text's characters. Equal text -> equal vector (so exact-match queries rank first).
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 8;

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIM];
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                v[i % DIM] += (text.charAt(i) % 32) + 1;
            }
        }
        double norm = 0;
        for (float f : v) norm += f * f;
        norm = Math.sqrt(norm) + 1e-6;
        for (int i = 0; i < DIM; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIM;
    }
}
