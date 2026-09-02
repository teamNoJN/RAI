package com.rai.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter();

    @Test
    void splitsWithOverlap() {
        List<String> chunks = splitter.split("a".repeat(2500), 1000, 200);
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allMatch(c -> c.length() <= 1000);
    }

    @Test
    void rejectsOverlapNotSmallerThanChunkSize() {
        assertThatThrownBy(() -> splitter.split("abc", 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
