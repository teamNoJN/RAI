package com.rai.chat.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rai.chat.dto.ChatDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

/**
 * 판정 근거를 규제 KB 에서 가져온다. 지금은 모놀리식 앱(:8090)이 규제를 갖고 있고,
 * ai-service 가 분리되면 URL 만 바뀐다.
 */
@Slf4j
@Component
public class RegulationClient {

    /**
     * /internal/regulations 응답. 응답 DTO(ChatDto.SourceResponse)를 그대로 쓰면
     * 바깥 계약의 필드명(version)과 내부 계약(document_version)이 엉켜 값이 조용히 유실된다.
     * 입력 전용 레코드를 따로 두고 이름을 명시한다.
     */
    public record InternalSource(
            @JsonProperty("document_id") String documentId,
            @JsonProperty("title") String title,
            @JsonProperty("authority") String authority,
            @JsonProperty("document_version") String documentVersion,
            @JsonProperty("effective_date") java.time.LocalDate effectiveDate,
            @JsonProperty("section") String section,
            @JsonProperty("source_url") String sourceUrl) {

        ChatDto.SourceResponse toSource() {
            return new ChatDto.SourceResponse(documentId, title, authority,
                    documentVersion, effectiveDate, section, sourceUrl);
        }
    }

    private final RestClient restClient;

    public RegulationClient(RestClient regulationRestClient) {
        this.restClient = regulationRestClient;
    }

    /**
     * 실패해도 예외를 올리지 않고 빈 목록을 준다 — 근거 없음으로 취급돼
     * 3R 가드레일(REVIEW_REQUIRED)로 안전하게 떨어진다. 없는 근거를 지어내는 것보다 낫다.
     */
    public List<ChatDto.SourceResponse> findSources(String countryId) {
        try {
            InternalSource[] sources = restClient.get()
                    .uri("/internal/regulations?country_id={countryId}", countryId)
                    .retrieve()
                    .body(InternalSource[].class);
            return sources == null ? List.of()
                    : Arrays.stream(sources).map(InternalSource::toSource).toList();
        } catch (RestClientException e) {
            log.warn("규제 KB 조회 실패 — 근거 없음으로 판정한다. countryId={}", countryId, e);
            return List.of();
        }
    }
}
