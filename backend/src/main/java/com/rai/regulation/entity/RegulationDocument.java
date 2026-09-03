package com.rai.regulation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * 규제 문서 Metadata (PRD 3. 데이터 요구사항).
 * DDL 은 init-db/01_schema.sql 의 regulation 테이블이 소유한다 — 컬럼명·타입을 그대로 따를 것.
 */
@Entity
@Table(name = "regulation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationDocument {

    /** 내부 PK. 외부 식별자는 documentId 다. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "regulation_id", updatable = false, nullable = false)
    private UUID regulationId;

    /** 외부 식별자 (예: VN-REG-001). UNIQUE. */
    @Column(name = "document_id", nullable = false, unique = true, length = 100)
    private String documentId;

    @Column(name = "country_id", nullable = false, length = 10)
    private String countryId;

    @Column(length = 255)
    private String authority;

    @Column(length = 500)
    private String title;

    @Column(name = "document_version", length = 50)
    private String documentVersion;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(length = 50)
    private String section;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /** 업로드 원본 파일 경로. */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
