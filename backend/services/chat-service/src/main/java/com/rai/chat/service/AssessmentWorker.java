package com.rai.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rai.chat.assessment.AssessmentInput;
import com.rai.chat.assessment.Assessor;
import com.rai.chat.assessment.RegulationRetriever;
import com.rai.chat.assessment.RetrievalQuery;
import com.rai.chat.client.DrugServiceClient;
import com.rai.chat.dto.ChatDto;
import com.rai.chat.entity.Assessment;
import com.rai.chat.entity.Message;
import com.rai.chat.entity.Source;
import com.rai.chat.repository.AssessmentRepository;
import com.rai.chat.repository.MessageRepository;
import com.rai.chat.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 판정 백그라운드 작업 (POST messages 202 이후 단계).
 *
 * <p>ChatService 와 같은 클래스에 두면 self-invocation 이라 프록시를 안 타서 @Async 가 무시된다.
 * 반드시 별도 빈으로 유지할 것 — 보고서의 ReportGenerationWorker 와 같은 이유다.
 *
 * <p>트랜잭션을 길게 열지 않는다. ai-service 가 붙으면 판정이 수십 초 걸리는데
 * 그동안 커넥션을 잡고 있을 이유가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentWorker {

    static final String STATUS_COMPLETED = "completed";
    static final String STATUS_FAILED = "failed";

    private final AssessmentRepository assessmentRepository;
    private final SourceRepository sourceRepository;
    private final MessageRepository messageRepository;
    private final DrugServiceClient drugServiceClient;
    private final RegulationRetriever regulationRetriever;
    private final Assessor assessor;
    private final ObjectMapper objectMapper;

    @Async("assessmentTaskExecutor")
    public void assess(String requestId, UUID assistantMessageId, UUID companyId, String question) {
        Assessment assessment = assessmentRepository.findById(requestId).orElse(null);
        if (assessment == null) {
            log.warn("판정 대상을 찾을 수 없습니다: {}", requestId);
            return;
        }
        try {
            DrugServiceClient.InternalDrug drug =
                    drugServiceClient.requireDrug(assessment.getDrugId(), companyId);

            List<String> ingredients = ingredientsOf(drug);

            // 근거 조회가 실패하면 빈 목록이 온다 → 3R 가드레일로 떨어진다(지어내지 않는다).
            List<ChatDto.SourceResponse> sources = regulationRetriever.retrieve(
                    new RetrievalQuery(assessment.getCountryId(), question, ingredients));

            ChatDto.Result result = assessor.assess(new AssessmentInput(
                    question, drug.product_name(), ingredients,
                    drug.strength(), drug.dosage_form(),
                    assessment.getCountryId(), sources));

            // 3N 재판정: 같은 대화의 직전 완료 판정과 결과가 다르면 changed_from 으로 알린다.
            String previous = assessmentRepository
                    .findFirstByConversationIdAndStatusOrderByCreatedAtDesc(
                            assessment.getConversationId(), STATUS_COMPLETED)
                    .map(Assessment::getEligibility)
                    .orElse(null);
            if (previous != null && !previous.equals(result.eligibility())) {
                result = result.withChangedFrom(previous);
            }

            // 판정 시점 근거를 값으로 복사해 박제한다 — 규제가 개정돼도 근거가 남아야 한다.
            sourceRepository.saveAll(sources.stream().map(s -> Source.builder()
                    .requestId(requestId)
                    .documentId(s.documentId())
                    .title(s.title())
                    .authority(s.authority())
                    .documentVersion(s.version())
                    .effectiveDate(s.effectiveDate())
                    .section(s.section())
                    .sourceUrl(s.sourceUrl())
                    .build()).toList());

            assessment.setDrugVersion(drug.version());
            assessment.setEligibility(result.eligibility());
            assessment.setSummary(result.summary());
            assessment.setResult(toJson(result));
            assessment.setStatus(STATUS_COMPLETED);
            finishMessage(assistantMessageId, STATUS_COMPLETED, result.summary());
            log.info("판정 완료: {} → {}", requestId, result.eligibility());
        } catch (Exception e) {
            // 예외를 삼키고 상태만 failed 로 남긴다 — FE 는 폴링 응답 status 로 3E 를 띄운다.
            log.error("판정 실패: {}", requestId, e);
            assessment.setStatus(STATUS_FAILED);
            finishMessage(assistantMessageId, STATUS_FAILED, "판정에 실패했습니다.");
        }
        assessmentRepository.save(assessment);
    }

    private List<String> ingredientsOf(DrugServiceClient.InternalDrug drug) {
        return drug.ingredients() == null ? List.of() : drug.ingredients();
    }

    private void finishMessage(UUID messageId, String status, String content) {
        messageRepository.findById(messageId).ifPresent(message -> {
            message.setStatus(status);
            message.setContent(content);
            messageRepository.save(message);
        });
    }

    private String toJson(ChatDto.Result result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("판정 결과 직렬화 실패", e);
        }
    }
}
