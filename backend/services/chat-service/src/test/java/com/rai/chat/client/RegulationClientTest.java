package com.rai.chat.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내부 계약(snake_case)이 바깥 응답 계약(version 등)과 이름이 달라서, 한쪽만 바뀌면
 * 값이 예외 없이 조용히 null 이 된다. 실제로 한 번 유실됐던 지점이라 와이어 포맷을 고정한다.
 */
class RegulationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Test
    void 규제_KB_응답의_모든_필드가_근거로_옮겨진다() throws Exception {
        String json = """
                { "document_id": "VN-REG-001", "title": "의약품 등록 규정",
                  "authority": "Drug Administration of Vietnam",
                  "document_version": "2026.01", "effective_date": "2026-01-01",
                  "section": "4.2", "source_url": "https://example.test" }
                """;

        var source = objectMapper.readValue(json, RegulationClient.InternalSource.class).toSource();

        assertThat(source.documentId()).isEqualTo("VN-REG-001");
        assertThat(source.title()).isEqualTo("의약품 등록 규정");
        assertThat(source.authority()).isEqualTo("Drug Administration of Vietnam");
        // 내부 document_version → 바깥 version. 여기가 어긋나면 4번 근거 패널의
        // "판정 기준 · 지식베이스 반영 일자"가 빈칸으로 나간다.
        assertThat(source.version()).isEqualTo("2026.01");
        assertThat(source.effectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(source.section()).isEqualTo("4.2");
        assertThat(source.sourceUrl()).isEqualTo("https://example.test");
    }
}
