package com.rai.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** 사용자. 이메일 UNIQUE, 비밀번호는 BCrypt 해시만 저장한다. */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    // v0.4 Auth 응답에는 role 이 없다. 스키마 컬럼(NOT NULL)만 채운다.
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "member";

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
