package com.rai.report.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 보고서 (API 명세 5번 · 5L번).
 * DDL 은 /init-db/01_schema.sql 의 report 테이블과 동일하게 유지할 것.
 *
 * 수정(PATCH)은 draft_content 를 덮어쓰고 version 만 증가시킨다 — 과거 본문은 보관하지 않는다.
 */
@Entity
@Table(name = "report")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    /** 생성 직후 job_id 로 그대로 반환하므로 애플리케이션에서 값을 만든다. */
    @Id
    @GeneratedValue
    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "draft_content", columnDefinition = "text")
    private String draftContent;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /** pending → completed | failed (init-db CHECK 제약과 동일) */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = ReportStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public boolean isCompleted() {
        return ReportStatus.COMPLETED.equals(status);
    }
}
