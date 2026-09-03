package com.rai.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 판정 근거 스냅샷. 규제가 개정·삭제돼도 판정 당시 값이 남아야 하므로
 * 참조가 아니라 값을 복사해 박제한다 (init-db/01_schema.sql).
 */
@Entity
@Table(name = "source")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "source_id", updatable = false, nullable = false)
    private UUID sourceId;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "document_id", nullable = false, length = 100)
    private String documentId;

    @Column(length = 500)
    private String title;

    @Column(length = 255)
    private String authority;

    @Column(name = "document_version", length = 50)
    private String documentVersion;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(length = 50)
    private String section;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;
}
