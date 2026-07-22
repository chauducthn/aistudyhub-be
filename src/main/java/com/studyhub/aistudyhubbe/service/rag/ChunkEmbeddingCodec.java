package com.studyhub.aistudyhubbe.service.rag;

import org.springframework.stereotype.Component;

@Component
public class ChunkEmbeddingCodec {

    public String encode(float[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(values.length * 10);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }

    public float[] decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return new float[0];
        }
        String[] parts = raw.split(",");
        float[] values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Float.parseFloat(parts[i].trim());
        }
        return values;
    }
}
