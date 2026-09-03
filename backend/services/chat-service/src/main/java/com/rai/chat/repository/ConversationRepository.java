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

    /**
     * 같은 약·국가 조합의 기존 세션. create 가 이것을 재사용해 레일 '최근 대화'에
     * 같은 대화가 여러 개 쌓이는 것을 막는다.
     * 단건(Optional)이 아니라 목록으로 받는다 — 이 규칙이 없던 시절 데이터와
     * changeContext 로 국가를 옮겨 만들어진 중복이 이미 있을 수 있어
     * NonUniqueResultException 이 나면 안 된다. 가장 최근 것을 쓴다.
     */
    @Query("""
            SELECT c FROM Conversation c
             WHERE c.companyId = :companyId AND c.userId = :userId
               AND c.drugId = :drugId AND c.countryId = :countryId
             ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC
            """)
    List<Conversation> findByContext(@Param("companyId") UUID companyId,
                                     @Param("userId") UUID userId,
                                     @Param("drugId") UUID drugId,
                                     @Param("countryId") String countryId,
                                     Pageable pageable);

    /** 이 제품으로 대화한 국가 목록 (drug-service 의 재검토 배지용). */
    @Query("""
            SELECT DISTINCT c.countryId FROM Conversation c
             WHERE c.companyId = :companyId AND c.drugId = :drugId
            """)
    List<String> findPriorCountries(@Param("companyId") UUID companyId, @Param("drugId") UUID drugId);
}
