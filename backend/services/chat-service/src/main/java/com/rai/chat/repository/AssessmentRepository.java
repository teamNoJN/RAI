package com.rai.chat.repository;

import com.rai.chat.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, String> {

    /** 전송 중 중복 요청 차단 (screen-03 "전송 중 send 비활성"의 서버측 방어). */
    boolean existsByConversationIdAndStatus(UUID conversationId, String status);

    /** stale-pending 회수용 — 워커 유실로 pending 이 고아가 되면 여기서 걷어낸다. */
    List<Assessment> findByConversationIdAndStatus(UUID conversationId, String status);

    /** 3N 재판정 비교 대상 — 같은 대화의 직전 완료 판정. */
    Optional<Assessment> findFirstByConversationIdAndStatusOrderByCreatedAtDesc(
            UUID conversationId, String status);

    long countByRequestIdStartingWith(String prefix);
}
