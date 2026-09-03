package com.rai.drug.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 제품. company_id 로 회사 격리한다 — 이 값은 사용자가 보내는 게 아니라
 * 토큰/Gateway 에서 온 값을 쓴다 (docs/00-project-plan.md).
 * DDL 은 init-db/01_schema.sql 이 소유한다.
 */
@Entity
@Table(name = "drug")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "drug_id", updatable = false, nullable = false)
    private UUID drugId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /** 성분 배열. jsonb 컬럼 ↔ List&lt;String&gt; 매핑. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> ingredients;

    @Column(length = 100)
    private String strength;

    @Column(name = "dosage_form", length = 100)
    private String dosageForm;

    /** PATCH 시 증가(덮어쓰지 않음). 등록 시 1. */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    // DB 기본값(now())이 정본. INSERT 직후 그 값을 다시 읽어온다.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // DB 기본값(now())이 정본. INSERT 직후 그 값을 다시 읽어온다.
    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
