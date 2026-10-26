package com.studymuse.documents;

import com.studymuse.domain.Chunk;

import java.util.ArrayList;
import java.util.List;

/** Splits a material's text into numbered chunks of about 500 words, the unit used for retrieval. */
public class TextChunker {

    private final int chunkSize;

    public TextChunker() {
        this(500);
    }

    public TextChunker(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public List<Chunk> split(String materialId, String sourceName, String text) {
        List<Chunk> chunks = new ArrayList<>();
        String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
        for (int start = 0, index = 0; start < words.length; start += chunkSize, index++) {
            int end = Math.min(words.length, start + chunkSize);
            String body = String.join(" ", java.util.Arrays.copyOfRange(words, start, end));
            chunks.add(new Chunk(materialId + "-c" + index, materialId, sourceName, index, body));
        }
        return chunks;
    }

    public static int countWords(String text) {
        String t = text.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }
}
