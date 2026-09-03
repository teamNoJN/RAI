package com.rai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 판정. 본문(result)은 AI 계약이 바뀌어도 스키마를 고정하지 않으려고 JSONB 원문으로 둔다.
 * 필터에 쓰는 status·eligibility 만 컬럼으로 승격돼 있다 (init-db/01_schema.sql).
 *
 * <p>보고서 모듈(backend/src의 ReportQueryRepository)이 이 테이블을 읽으므로
 * 컬럼 의미를 바꾸면 5번 화면이 깨진다.
 */
@Entity
@Table(name = "assessment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assessment {

    /** 명세상 판정 식별자 (예: req_001). PK 라 자동 생성이 아니다. */
    @Id
    @Column(name = "request_id", length = 50)
    private String requestId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "drug_id", nullable = false)
    private UUID drugId;

    @Column(name = "country_id", nullable = false, length = 10)
    private String countryId;

    /** 판정 시점 제품 버전. 제품이 수정되면 재판정이 필요한지 판단하는 근거. */
    @Column(name = "drug_version")
    private Integer drugVersion;

    /** pending | completed | failed */
    @Column(nullable = false, length = 20)
    private String status;

    /** POSSIBLE | CONDITIONAL | REVIEW_REQUIRED | RESTRICTED */
    @Column(length = 20)
    private String eligibility;

    @Column(columnDefinition = "text")
    private String summary;

    /** {ingredient_assessments[], requirements[], risks[], recommended_actions[]} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String result;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
