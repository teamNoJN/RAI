package com.rai.regulation.repository;

import com.rai.regulation.entity.RegulationChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegulationChunkRepository extends JpaRepository<RegulationChunk, Long> {
    List<RegulationChunk> findByDocumentDocumentIdOrderByChunkIndex(String documentId);
    long countByDocumentDocumentId(String documentId);
}
