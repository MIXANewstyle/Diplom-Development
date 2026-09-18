package com.diplom.chatservice.diary.support;

import com.diplom.chatservice.llm.EmbeddingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic stand-in for the embeddings provider: each word hashes into one dimension, the
 * vector is L2-normalized. Texts that share words get a high cosine similarity, so the RAG
 * retrieval path (threshold, per-day cap, ordering) is exercised for real without any network.
 */
public final class BagOfWordsEmbeddingClient implements EmbeddingClient {

    private final int dimensions;

    public BagOfWordsEmbeddingClient(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        List<float[]> out = new ArrayList<>(inputs.size());
        for (String in : inputs) out.add(embedText(in));
        return out;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return "test/bag-of-words";
    }

    private float[] embedText(String text) {
        float[] v = new float[dimensions];
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (w.isEmpty()) continue;
            v[Math.floorMod(w.hashCode(), dimensions)] += 1f;
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        if (norm == 0) { v[0] = 1f; return v; }
        norm = Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }
}
