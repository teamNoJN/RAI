package com.rai.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 고정 길이 + overlap 방식의 단순 청크 분할. Section 기반 분할은 추후 개선. */
@Component
public class ChunkSplitter {

    public static final int DEFAULT_CHUNK_SIZE = 1000;
    public static final int DEFAULT_OVERLAP = 200;

    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> split(String text, int chunkSize, int overlap) {
        if (chunkSize <= overlap) {
            throw new IllegalArgumentException("chunkSize 는 overlap 보다 커야 합니다");
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == text.length()) {
                break;
            }
            start = end - overlap;
        }
        return chunks;
    }
}
