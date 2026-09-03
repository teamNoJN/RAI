package com.rai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/** 대화 타임라인 한 줄 (screen-03). assistant 메시지만 intent/status 를 갖는다. */
@Entity
@Table(name = "message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id", updatable = false, nullable = false)
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    /** user | assistant (스키마 CHECK 제약). */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** assistant 만. EXPORT_ELIGIBILITY_CHECK 등. */
    @Column(length = 50)
    private String intent;

    /** pending | completed | failed. 비동기 판정 진행 상태. */
    @Column(length = 20)
    private String status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
