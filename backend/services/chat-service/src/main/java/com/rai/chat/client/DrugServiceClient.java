package com.rai.chat.client;

import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import com.rai.common.security.AuthHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * chat → drug 내부 호출. conversation.drug_id 는 서비스 경계를 넘는 값이라
 * 저장 전에 여기서 존재·소유를 확인한다 (docs/00-project-plan.md ③).
 */
@Slf4j
@Component
public class DrugServiceClient {

    /** /internal/drugs 응답 (snake_case). */
    public record InternalDrug(String drug_id, String product_name) {}

    private final RestClient restClient;

    public DrugServiceClient(RestClient drugRestClient) {
        this.restClient = drugRestClient;
    }

    /** 제품이 없거나 다른 회사 것이면 404 를 그대로 올린다 (screen-02 POST /api/conversations 404). */
    public InternalDrug requireDrug(UUID drugId, UUID companyId) {
        try {
            return restClient.get()
                    .uri("/internal/drugs/{drugId}", drugId)
                    .header(AuthHeaders.COMPANY_ID, companyId.toString())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new ApiException(ErrorCode.NOT_FOUND, "제품을 찾을 수 없습니다");
                    })
                    .body(InternalDrug.class);
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            // 확인하지 못한 채로 세션을 만들면 잘못된 컨텍스트가 고정된다. 여기서는 흡수하지 않는다.
            log.error("drug-service 호출 실패. drugId={}", drugId, e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 최근 대화 목록의 제품명 배치 조회. 이름을 못 채워도 목록 자체는 떠야 하므로
     * 실패는 빈 맵으로 흡수한다.
     */
    public Map<UUID, String> productNames(List<UUID> drugIds, UUID companyId) {
        if (drugIds.isEmpty()) {
            return Map.of();
        }
        try {
            InternalDrug[] drugs = restClient.get()
                    .uri("/internal/drugs?ids={ids}",
                            drugIds.stream().map(UUID::toString).collect(Collectors.joining(",")))
                    .header(AuthHeaders.COMPANY_ID, companyId.toString())
                    .retrieve()
                    .body(InternalDrug[].class);
            if (drugs == null) {
                return Map.of();
            }
            return java.util.Arrays.stream(drugs).collect(
                    Collectors.toMap(d -> UUID.fromString(d.drug_id()), InternalDrug::product_name,
                            (a, b) -> a));
        } catch (RestClientException e) {
            log.warn("drug-service 제품명 조회 실패 — 제품명 없이 목록을 준다.", e);
            return Map.of();
        }
    }

    /** 목록 전용 헬퍼. */
    public Function<UUID, String> nameLookup(List<UUID> drugIds, UUID companyId) {
        Map<UUID, String> names = productNames(drugIds, companyId);
        return names::get;
    }
}
