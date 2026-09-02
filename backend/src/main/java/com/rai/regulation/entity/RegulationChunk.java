package com.rai.regulation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 파싱된 규제 문서 청크 + 임베딩 (RAG Retrieval 대상).
 * embedding 차원(1536)은 /init-db/01_init.sql 의 VECTOR(1536) 과 일치해야 함.
 */
@Entity
@Table(name = "regulation_chunks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulationChunk {

    public static final int EMBEDDING_DIMENSION = 1536;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long chunkId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private RegulationDocument document;

    @Column(length = 64)
    private String section;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSION)
    private float[] embedding;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
