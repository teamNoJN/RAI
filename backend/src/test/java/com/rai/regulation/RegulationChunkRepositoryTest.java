package com.rai.regulation;

import com.rai.regulation.entity.RegulationChunk;
import com.rai.regulation.entity.RegulationDocument;
import com.rai.regulation.repository.RegulationChunkRepository;
import com.rai.regulation.repository.RegulationDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** pgvector 컬럼 매핑(float[] <-> VECTOR)이 실제 DB 에서 동작하는지 확인. */
@SpringBootTest
@ActiveProfiles("local")
class RegulationChunkRepositoryTest {

    private static final String DOC_ID = "TEST-VECTOR-001";

    @Autowired RegulationDocumentRepository documentRepository;
    @Autowired RegulationChunkRepository chunkRepository;

    @AfterEach
    void cleanup() {
        documentRepository.deleteById(DOC_ID); // chunks 는 ON DELETE CASCADE
    }

    @Test
    void savesAndReadsEmbedding() {
        RegulationDocument doc = documentRepository.save(RegulationDocument.builder()
                .documentId(DOC_ID).country("VN").authority("Test").title("Vector mapping test").build());

        float[] embedding = new float[RegulationChunk.EMBEDDING_DIMENSION];
        embedding[0] = 0.5f;
        embedding[1535] = -1f;
        RegulationChunk saved = chunkRepository.save(RegulationChunk.builder()
                .document(doc).chunkIndex(0).content("hello").embedding(embedding).build());

        RegulationChunk loaded = chunkRepository.findById(saved.getChunkId()).orElseThrow();
        assertThat(loaded.getEmbedding()).hasSize(RegulationChunk.EMBEDDING_DIMENSION);
        assertThat(loaded.getEmbedding()[0]).isEqualTo(0.5f);
        assertThat(loaded.getEmbedding()[1535]).isEqualTo(-1f);
    }
}
