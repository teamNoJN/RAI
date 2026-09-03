package com.rai.report.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 보고서 초안 생성 작업 (screen-04 [이 근거로 보고서에 반영] → screen-05 편집).
 * DDL 은 /init-db/01_schema.sql 의 report 테이블과 동일하게 유지할 것.
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

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    @Column(nullable = false, length = 20)
    private String status;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
