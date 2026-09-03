package com.rai.regulation.repository;

import com.rai.regulation.entity.RegulationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PK 는 regulation_id(UUID). 외부에서 쓰는 식별자는 document_id 라 조회 메서드를 따로 둔다. */
public interface RegulationDocumentRepository extends JpaRepository<RegulationDocument, UUID> {

    Optional<RegulationDocument> findByDocumentId(String documentId);

    boolean existsByDocumentId(String documentId);

    List<RegulationDocument> findByCountryIdOrderByCreatedAtDesc(String countryId);

    List<RegulationDocument> findAllByOrderByCreatedAtDesc();
}
