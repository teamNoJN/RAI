package com.rai.drug.repository;

import com.rai.drug.entity.Drug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DrugRepository extends JpaRepository<Drug, UUID> {

    List<Drug> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<Drug> findByDrugIdAndCompanyId(UUID drugId, UUID companyId);

    /** 2F 중복 등록 방지 — 같은 회사 안에서 제품명은 유일해야 한다. */
    boolean existsByCompanyIdAndProductName(UUID companyId, String productName);

    List<Drug> findByCompanyIdAndDrugIdIn(UUID companyId, List<UUID> drugIds);

    /**
     * 2S 약 검색 — 제품명과 성분 양쪽을 본다. ingredients 는 jsonb 라 텍스트로 캐스팅해 비교한다.
     * 회사 격리(company_id)를 WHERE 에서 절대 빼지 말 것.
     */
    @Query(value = """
            SELECT * FROM drug
             WHERE company_id = :companyId
               AND (product_name ILIKE '%' || :keyword || '%'
                    OR ingredients::text ILIKE '%' || :keyword || '%')
             ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Drug> search(@Param("companyId") UUID companyId, @Param("keyword") String keyword);
}
