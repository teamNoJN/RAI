package com.rai.report.repository;

import com.rai.report.dto.ReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * report 가 참조하는 다른 도메인(conversation · assessment · source)의 읽기 전용 조회.
 *
 * 해당 도메인의 JPA 엔티티는 3·4번 담당자 영역이라 아직 없다. 엔티티를 선점해 만들면 머지 충돌이
 * 나므로, 여기서는 네이티브 쿼리로만 읽는다. 엔티티가 머지되면 이 클래스를 JPQL 로 걷어내면 된다.
 */
@Repository
@RequiredArgsConstructor
public class ReportQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<ReportDto.SourceResponse> SOURCE_MAPPER = (rs, rowNum) ->
            ReportDto.SourceResponse.builder()
                    .documentId(rs.getString("document_id"))
                    .title(rs.getString("title"))
                    .authority(rs.getString("authority"))
                    .version(rs.getString("document_version"))
                    .effectiveDate(Optional.ofNullable(rs.getDate("effective_date")).map(Date::toLocalDate).orElse(null))
                    .section(rs.getString("section"))
                    .sourceUrl(rs.getString("source_url"))
                    .build();

    private static final RowMapper<ReportDto.ListItem> LIST_ITEM_MAPPER = (rs, rowNum) ->
            ReportDto.ListItem.builder()
                    .reportId(rs.getObject("report_id", UUID.class))
                    .drugId(rs.getObject("drug_id", UUID.class))
                    .countryId(rs.getString("country_id"))
                    .status(rs.getString("status"))
                    .version(rs.getInt("version"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .build();

    /**
     * 5L 보관함 목록. drug_id · country_id 는 report 에 없어 conversation 을 조인한다.
     * 생성 중(pending)·실패(failed)는 보여줄 본문이 없으므로 목록에서 뺀다.
     *
     * @param companyId null 이면 회사 필터를 걸지 않는다 (Gateway 가 X-Company-Id 를 넣기 전 단계)
     */
    public List<ReportDto.ListItem> findCompletedListItems(UUID companyId) {
        String sql = """
                SELECT r.report_id, c.drug_id, c.country_id, r.status, r.version, r.created_at
                  FROM report r
                  JOIN conversation c ON c.conversation_id = r.conversation_id
                 WHERE r.status = 'completed'
                """;
        if (companyId == null) {
            return jdbcTemplate.query(sql + " ORDER BY r.created_at DESC", LIST_ITEM_MAPPER);
        }
        return jdbcTemplate.query(sql + "   AND c.company_id = ? ORDER BY r.created_at DESC",
                LIST_ITEM_MAPPER, companyId);
    }

    /** 판정 근거 스냅샷 — 규제가 개정돼도 판정 당시 값이 그대로 나온다. */
    public List<ReportDto.SourceResponse> findSources(String requestId) {
        return jdbcTemplate.query("""
                SELECT document_id, title, authority, document_version, effective_date, section, source_url
                  FROM source
                 WHERE request_id = ?
                 ORDER BY document_id
                """, SOURCE_MAPPER, requestId);
    }

    public Optional<AssessmentSnapshot> findAssessment(String requestId) {
        return jdbcTemplate.query("""
                SELECT request_id, conversation_id, status, eligibility, summary, result::text AS result_json
                  FROM assessment
                 WHERE request_id = ?
                """, (rs, rowNum) -> new AssessmentSnapshot(
                        rs.getString("request_id"),
                        rs.getObject("conversation_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("eligibility"),
                        rs.getString("summary"),
                        rs.getString("result_json")),
                requestId).stream().findFirst();
    }

    public boolean conversationExists(UUID conversationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM conversation WHERE conversation_id = ?)",
                Boolean.class, conversationId));
    }
}
