package com.rai.parser;

import com.rai.regulation.entity.RegulationChunk;
import com.rai.regulation.entity.RegulationDocument;
import com.rai.regulation.repository.RegulationChunkRepository;
import com.rai.regulation.repository.RegulationDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 규제 문서 파서: 텍스트 추출 → 청크 분할 → (임베딩) → pgvector 저장.
 * 운영자가 검수한 문서를 수동 등록할 때 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParserService {

    private final TextExtractor textExtractor;
    private final ChunkSplitter chunkSplitter;
    private final RegulationDocumentRepository documentRepository;
    private final RegulationChunkRepository chunkRepository;

    @Transactional
    public int ingest(RegulationDocument document, String filename, InputStream input) throws IOException {
        if (documentRepository.existsById(document.getDocumentId())) {
            throw new IllegalArgumentException("이미 등록된 document_id 입니다: " + document.getDocumentId());
        }
        String text = textExtractor.extract(filename, input);
        List<String> chunks = chunkSplitter.split(text);
        log.info("[Parser] {}: {} chars -> {} chunks", filename, text.length(), chunks.size());

        documentRepository.save(document);
        for (int i = 0; i < chunks.size(); i++) {
            chunkRepository.save(RegulationChunk.builder()
                    .document(document)
                    .section(document.getSection())
                    .chunkIndex(i)
                    .content(chunks.get(i))
                    .embedding(null) // TODO: Embedding Adapter 연동
                    .build());
        }
        return chunks.size();
    }
}
