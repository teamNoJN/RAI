package com.rai.regulation.repository;

import com.rai.regulation.entity.RegulationRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulationRevisionRepository extends JpaRepository<RegulationRevision, UUID> {

    List<RegulationRevision> findAllByOrderByCreatedAtDesc();

    List<RegulationRevision> findByCountryIdOrderByCreatedAtDesc(String countryId);

    List<RegulationRevision> findByReviewStatusOrderByCreatedAtDesc(String reviewStatus);

    List<RegulationRevision> findByCountryIdAndReviewStatusOrderByCreatedAtDesc(String countryId, String reviewStatus);

    /** app_user 엔티티가 이 모듈에 없어 이름만 조회한다. 감사 기록(reflected_by) 표시용. */
    @Query(value = "SELECT name FROM app_user WHERE user_id = :userId", nativeQuery = true)
    Optional<String> findUserName(@Param("userId") UUID userId);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM app_user WHERE user_id = :userId)", nativeQuery = true)
    boolean existsUser(@Param("userId") UUID userId);
}
