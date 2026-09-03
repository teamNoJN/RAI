package com.rai.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * 회사(테넌트). 회원가입 시 company_name 이 기존과 같으면 자동 소속,
 * 새 이름이면 생성한다 (docs/api-spec/screen-01-login.md).
 * DDL 은 init-db/01_schema.sql 이 소유한다.
 */
@Entity
@Table(name = "company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "company_id", updatable = false, nullable = false)
    private UUID companyId;

    @Column(name = "company_name", nullable = false, unique = true, length = 255)
    private String companyName;

    // DB 기본값(now())이 정본. INSERT 직후 그 값을 다시 읽어온다.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
