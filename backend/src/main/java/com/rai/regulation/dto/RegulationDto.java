package com.rai.regulation.dto;

import com.rai.regulation.entity.RegulationDocument;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.Instant;

public class RegulationDto {

    /** 규제 문서 등록 (파일 + Metadata). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IngestRequest {
        @NotBlank(message = "document_id 는 필수입니다")
        private String documentId;
        @NotBlank(message = "country 는 필수입니다")
        private String country;
        @NotBlank(message = "authority 는 필수입니다")
        private String authority;
        @NotBlank(message = "title 은 필수입니다")
        private String title;
        private String documentVersion;
        private LocalDate publishedDate;
        private LocalDate effectiveDate;
        private String section;
        private String sourceUrl;

        public RegulationDocument toEntity() {
            return RegulationDocument.builder()
                    .documentId(documentId)
                    .country(country)
                    .authority(authority)
                    .title(title)
                    .documentVersion(documentVersion)
                    .publishedDate(publishedDate)
                    .effectiveDate(effectiveDate)
                    .section(section)
                    .sourceUrl(sourceUrl)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class DocumentResponse {
        private String documentId;
        private String country;
        private String authority;
        private String title;
        private String documentVersion;
        private LocalDate publishedDate;
        private LocalDate effectiveDate;
        private String section;
        private String sourceUrl;
        private String status;
        private long chunkCount;
        private Instant createdAt;
        private Instant updatedAt;

        public static DocumentResponse from(RegulationDocument d, long chunkCount) {
            return DocumentResponse.builder()
                    .documentId(d.getDocumentId())
                    .country(d.getCountry())
                    .authority(d.getAuthority())
                    .title(d.getTitle())
                    .documentVersion(d.getDocumentVersion())
                    .publishedDate(d.getPublishedDate())
                    .effectiveDate(d.getEffectiveDate())
                    .section(d.getSection())
                    .sourceUrl(d.getSourceUrl())
                    .status(d.getStatus())
                    .chunkCount(chunkCount)
                    .createdAt(d.getCreatedAt())
                    .updatedAt(d.getUpdatedAt())
                    .build();
        }
    }
}
