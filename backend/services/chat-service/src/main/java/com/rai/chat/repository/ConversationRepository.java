package com.rai.chat.repository;

import com.rai.chat.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * 레일 "최근 대화" — 마지막 메시지 시각 기준. 아직 메시지가 없는 새 세션은
     * last_message_at 이 null 이라 생성 시각으로 대신 정렬한다.
     */
    @Query("""
            SELECT c FROM Conversation c
             WHERE c.companyId = :companyId AND c.userId = :userId
             ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC
            """)
    List<Conversation> findRecent(@Param("companyId") UUID companyId,
                                  @Param("userId") UUID userId,
                                  Pageable pageable);

    /** 이 제품으로 대화한 국가 목록 (drug-service 의 재검토 배지용). */
    @Query("""
            SELECT DISTINCT c.countryId FROM Conversation c
             WHERE c.companyId = :companyId AND c.drugId = :drugId
            """)
    List<String> findPriorCountries(@Param("companyId") UUID companyId, @Param("drugId") UUID drugId);
}
