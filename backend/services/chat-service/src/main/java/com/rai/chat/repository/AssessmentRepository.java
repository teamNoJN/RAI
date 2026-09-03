package com.rai.chat.repository;

import com.rai.chat.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, String> {

    /** 전송 중 중복 요청 차단 (screen-03 "전송 중 send 비활성"의 서버측 방어). */
    boolean existsByConversationIdAndStatus(UUID conversationId, String status);

    long countByRequestIdStartingWith(String prefix);
}
