package com.rai.regulation.service;

import com.rai.regulation.dto.RegulationDto;
import com.rai.regulation.entity.RegulationDocument;
import com.rai.regulation.repository.RegulationChunkRepository;
import com.rai.regulation.repository.RegulationDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegulationService {

    private final RegulationDocumentRepository documentRepository;
    private final RegulationChunkRepository chunkRepository;

    public List<RegulationDto.DocumentResponse> listDocuments(String country) {
        List<RegulationDocument> documents = (country == null || country.isBlank())
                ? documentRepository.findAllByOrderByCreatedAtDesc()
                : documentRepository.findByCountryIdOrderByCreatedAtDesc(country);
        return documents.stream()
                .map(d -> RegulationDto.DocumentResponse.from(d, chunkRepository.countByDocumentDocumentId(d.getDocumentId())))
                .toList();
    }
}
