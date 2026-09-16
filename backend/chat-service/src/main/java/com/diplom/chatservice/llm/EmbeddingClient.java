package com.diplom.chatservice.llm;

import java.util.List;

public interface EmbeddingClient {

    /**
     * Embeds each input text. Returns one vector per input, in order; every vector has
     * exactly {@link #dimensions()} components.
     */
    List<float[]> embed(List<String> inputs);

    default float[] embedOne(String input) {
        return embed(List.of(input)).get(0);
    }

    int dimensions();

    String modelName();
}
