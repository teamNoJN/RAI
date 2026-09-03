package com.rai.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * 약 + 국가 컨텍스트를 고정한 대화 세션 (screen-02 → screen-03).
 *
 * <p>drug_id / country_id 는 다른 서비스가 소유하는 데이터라 엔티티 연관을 만들지 않고
 * 값만 저장한다. 유효성은 /internal 호출로 확인한다 (docs/00-project-plan.md).
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "conversation_id", updatable = false, nullable = false)
    private UUID conversationId;

    /** 회사 격리 캐시. 목록 조회에서 이 값을 반드시 조건에 넣는다. */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "drug_id", nullable = false)
    private UUID drugId;

    @Column(name = "country_id", nullable = false, length = 10)
    private String countryId;

    // DB 기본값(now())이 정본. INSERT 직후 그 값을 다시 읽어온다.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** 메시지가 오갈 때 갱신된다. 새 세션은 아직 null. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}
