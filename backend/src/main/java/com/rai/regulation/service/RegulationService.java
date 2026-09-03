package com.rai.regulation.service;

import com.rai.regulation.dto.RegulationDto;
import com.rai.regulation.dto.RegulationSourceDto;
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

    /**
     * 판정 근거 후보. 근거가 없으면 빈 목록을 주고, 호출측(chat-service)은 문서명·조항을
     * 지어내지 않고 REVIEW_REQUIRED 로 분기해야 한다 (screen-03r 가드레일).
     */
    public List<RegulationSourceDto> findSources(String countryId) {
        return documentRepository.findByCountryIdOrderByCreatedAtDesc(countryId).stream()
                .filter(d -> "ACTIVE".equals(d.getStatus()))
                .map(d -> new RegulationSourceDto(
                        d.getDocumentId(), d.getTitle(), d.getAuthority(),
                        d.getDocumentVersion(), d.getEffectiveDate(), d.getSection(), d.getSourceUrl()))
                .toList();
    }

    public List<RegulationDto.DocumentResponse> listDocuments(String country) {
        List<RegulationDocument> documents = (country == null || country.isBlank())
                ? documentRepository.findAllByOrderByCreatedAtDesc()
                : documentRepository.findByCountryIdOrderByCreatedAtDesc(country);
        return documents.stream()
                .map(d -> RegulationDto.DocumentResponse.from(d, chunkRepository.countByDocumentDocumentId(d.getDocumentId())))
                .toList();
    }
}
