package com.rai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/** 판정 카드의 👍 유용 / ✎ 수정 필요 (screen-03). */
@Entity
@Table(name = "feedback")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "feedback_id", updatable = false, nullable = false)
    private UUID feedbackId;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    /** helpful | needs_revision (스키마 CHECK 제약). */
    @Column(nullable = false, length = 20)
    private String rating;

    @Column(columnDefinition = "text")
    private String reason;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
