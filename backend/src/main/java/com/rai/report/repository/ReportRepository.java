package com.rai.report.repository;

import com.rai.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query(value = "SELECT EXISTS(SELECT 1 FROM conversation WHERE conversation_id = :conversationId)", nativeQuery = true)
    boolean existsConversation(@Param("conversationId") UUID conversationId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM assessment WHERE request_id = :requestId AND conversation_id = :conversationId)", nativeQuery = true)
    boolean existsAssessment(@Param("requestId") String requestId, @Param("conversationId") UUID conversationId);
}
