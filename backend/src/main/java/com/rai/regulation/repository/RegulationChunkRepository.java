package com.rai.regulation.repository;

import com.rai.regulation.entity.RegulationChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegulationChunkRepository extends JpaRepository<RegulationChunk, UUID> {
    List<RegulationChunk> findByDocumentDocumentIdOrderByChunkIndex(String documentId);
    long countByDocumentDocumentId(String documentId);
}
