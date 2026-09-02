package com.rai.regulation.repository;

import com.rai.regulation.entity.RegulationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegulationDocumentRepository extends JpaRepository<RegulationDocument, String> {
    List<RegulationDocument> findByCountryOrderByCreatedAtDesc(String country);
    List<RegulationDocument> findAllByOrderByCreatedAtDesc();
}
