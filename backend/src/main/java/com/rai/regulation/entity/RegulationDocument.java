package com.rai.regulation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.Instant;

/**
 * 규제 문서 Metadata (PRD 3. 데이터 요구사항).
 * DDL 은 /init-db/01_init.sql 과 동일하게 유지할 것.
 */
@Entity
@Table(name = "regulation_documents")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationDocument {

    @Id
    @Column(name = "document_id", length = 64)
    private String documentId;

    @Column(nullable = false, length = 8)
    private String country;

    @Column(nullable = false)
    private String authority;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "document_version", length = 64)
    private String documentVersion;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(length = 64)
    private String section;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

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
