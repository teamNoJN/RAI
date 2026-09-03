package com.rai.drug.client;

import com.rai.common.security.AuthHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * drug → chat 내부 호출. 서비스 경계를 넘는 FK 를 만들지 않는 대신
 * /internal/** 로 물어본다 (docs/00-project-plan.md ③).
 */
@Slf4j
@Component
public class ChatServiceClient {

    private final RestClient restClient;

    public ChatServiceClient(RestClient chatRestClient) {
        this.restClient = chatRestClient;
    }

    /**
     * 이 제품으로 이미 대화한 국가 목록. chat-service 가 죽어도 대시보드는 떠야 하므로
     * 실패는 "이력 없음"으로 흡수하고 로그만 남긴다.
     */
    public List<String> priorCountries(UUID drugId, UUID companyId) {
        try {
            String[] countries = restClient.get()
                    .uri("/internal/conversations/prior-countries?drug_id={drugId}", drugId)
                    .header(AuthHeaders.COMPANY_ID, companyId.toString())
                    .retrieve()
                    .body(String[].class);
            return countries == null ? List.of() : List.of(countries);
        } catch (RestClientException e) {
            log.warn("chat-service 조회 실패 — 재검토 배지를 끈 채로 응답한다. drugId={}", drugId, e);
            return List.of();
        }
    }
}
