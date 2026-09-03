package com.rai.regulation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 개정 규제 검수 대기열 (screen-06). 승인 전까지는 KB(regulation/regulation_chunk)와 무관한
 * 별도 워크플로 데이터다. DDL 은 init-db/01_schema.sql 의 regulation_revision 테이블과 일치해야 한다.
 */
@Entity
@Table(name = "regulation_revision")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "regulation_id")
    private UUID regulationId;

    @Column(name = "country_id", nullable = false, length = 10)
    private String countryId;

    @Column(name = "regulation_type", nullable = false, length = 50)
    private String regulationType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "before_content", columnDefinition = "text")
    private String beforeContent;

    @Column(name = "after_content", columnDefinition = "text")
    private String afterContent;

    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "review_status", nullable = false, length = 20)
    @Builder.Default
    private String reviewStatus = "PENDING";

    @Column(name = "reflected_at")
    private Instant reflectedAt;

    @Column(name = "reflected_by")
    private UUID reflectedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
