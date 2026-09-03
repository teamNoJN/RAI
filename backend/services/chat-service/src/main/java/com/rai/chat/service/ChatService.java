package com.rai.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rai.chat.assessment.IntentClassifier;
import com.rai.chat.client.DrugServiceClient;
import com.rai.chat.dto.ChatDto;
import com.rai.chat.dto.ConversationDto;
import com.rai.chat.entity.Assessment;
import com.rai.chat.entity.Conversation;
import com.rai.chat.entity.Feedback;
import com.rai.chat.entity.Message;
import com.rai.chat.repository.AssessmentRepository;
import com.rai.chat.repository.ConversationRepository;
import com.rai.chat.repository.FeedbackRepository;
import com.rai.chat.repository.MessageRepository;
import com.rai.chat.repository.SourceRepository;
import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import com.rai.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/** 3 · 3C · 3E · 3N · 3R 화면 (docs/api-spec/screen-03*.md). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_PENDING = "pending";
    private static final String PENDING_CONTENT = "판정 중입니다...";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AssessmentRepository assessmentRepository;
    private final SourceRepository sourceRepository;
    private final FeedbackRepository feedbackRepository;
    private final DrugServiceClient drugServiceClient;
    private final IntentClassifier intentClassifier;
    private final AssessmentWorker assessmentWorker;
    private final ObjectMapper objectMapper;

    /**
     * 전송 버튼 · 퀵 액션 칩 공용. 판정은 오래 걸릴 수 있어 202 로 먼저 답하고 뒤에서 처리한다.
     * FE 는 2초 간격으로 GET /api/assessments/{request_id} 를 폴링한다.
     */
    @Transactional
    public ChatDto.AssessmentResponse sendMessage(CurrentUser currentUser, UUID conversationId,
                                                  ChatDto.MessageRequest request) {
        Conversation conversation = requireConversation(currentUser, conversationId);

        // 전송 중 send 비활성(screen-03)의 서버측 방어. 연타로 판정이 중복 생성되면 안 된다.
        if (assessmentRepository.existsByConversationIdAndStatus(conversationId, STATUS_PENDING)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이전 요청을 처리 중입니다. 잠시 후 다시 시도해주세요");
        }

        String message = request.message().trim();
        String intent = intentClassifier.classify(message);

        messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .role(ROLE_USER)
                .content(message)
                .build());

        Message assistant = messageRepository.saveAndFlush(Message.builder()
                .conversationId(conversationId)
                .role(ROLE_ASSISTANT)
                .content(PENDING_CONTENT)
                .intent(intent)
                .status(STATUS_PENDING)
                .build());

        String requestId = nextRequestId();
        assessmentRepository.saveAndFlush(Assessment.builder()
                .requestId(requestId)
                .conversationId(conversationId)
                .drugId(conversation.getDrugId())
                .countryId(conversation.getCountryId())
                .status(STATUS_PENDING)
                .build());

        conversation.setLastMessageAt(java.time.Instant.now());

        // 커밋된 뒤에 깨운다. 트랜잭션 안에서 부르면 워커 스레드가 아직 커밋되지 않은
        // assessment 행을 못 찾아 판정이 pending 에 머문다 (ReportGenerationWorker 주석과 같은 함정).
        UUID assistantMessageId = assistant.getMessageId();
        UUID companyId = currentUser.companyId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                assessmentWorker.assess(requestId, assistantMessageId, companyId, message);
            }
        });

        return ChatDto.AssessmentResponse.pending(requestId, intent,
                new ChatDto.Context(conversation.getDrugId().toString(), conversation.getCountryId()));
    }

    /** 진입·이어하기 시 타임라인 복원. 3N 의 후속 알림 메시지도 같은 목록으로 나온다. */
    @Transactional(readOnly = true)
    public List<ChatDto.MessageResponse> listMessages(CurrentUser currentUser, UUID conversationId) {
        requireConversation(currentUser, conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(ChatDto.MessageResponse::from)
                .toList();
    }

    /** 202 수신 후 폴링 대상. 실패면 상태만 준다(3E). */
    @Transactional(readOnly = true)
    public ChatDto.AssessmentResponse getAssessment(CurrentUser currentUser, String requestId) {
        Assessment assessment = requireAssessment(currentUser, requestId);

        if (AssessmentWorker.STATUS_FAILED.equals(assessment.getStatus())) {
            return ChatDto.AssessmentResponse.failed(requestId);
        }

        ChatDto.Context context = new ChatDto.Context(
                assessment.getDrugId().toString(), assessment.getCountryId());
        String intent = IntentClassifier.EXPORT_ELIGIBILITY_CHECK;

        if (STATUS_PENDING.equals(assessment.getStatus())) {
            return ChatDto.AssessmentResponse.pending(requestId, intent, context);
        }

        List<ChatDto.SourceResponse> sources = sourceRepository.findByRequestId(requestId).stream()
                .map(ChatDto.SourceResponse::from)
                .toList();

        return new ChatDto.AssessmentResponse(requestId, assessment.getStatus(), intent, context,
                readResult(assessment), sources);
    }

    /** 판정 카드 👍/✎. */
    @Transactional
    public ChatDto.FeedbackResponse recordFeedback(CurrentUser currentUser, String requestId,
                                                   ChatDto.FeedbackRequest request) {
        requireAssessment(currentUser, requestId);
        feedbackRepository.save(Feedback.builder()
                .requestId(requestId)
                .rating(request.rating())
                .reason(request.reason())
                .build());
        return ChatDto.FeedbackResponse.recorded();
    }

    /**
     * 3C 컨텍스트 변경 — 국가만 바꾼다. 약을 바꾸면 이전 판정·보고서가 어느 제품 것인지
     * 알 수 없게 되므로 명세대로 새 세션으로 안내한다.
     */
    @Transactional
    public ConversationDto.CreateResponse changeContext(CurrentUser currentUser, UUID conversationId,
                                                        ChatDto.ContextRequest request) {
        Conversation conversation = requireConversation(currentUser, conversationId);

        if (!drugServiceClient.countryExists(request.countryId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "국가를 찾을 수 없습니다: " + request.countryId());
        }
        conversation.setCountryId(request.countryId());
        return ConversationDto.CreateResponse.from(conversation);
    }

    // --- 내부 ---------------------------------------------------------

    /** 다른 회사·다른 사용자의 대화는 존재해도 404 로 가린다. */
    private Conversation requireConversation(CurrentUser currentUser, UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(c -> c.getCompanyId().equals(currentUser.companyId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "대화를 찾을 수 없습니다"));
    }

    private Assessment requireAssessment(CurrentUser currentUser, String requestId) {
        Assessment assessment = assessmentRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "판정을 찾을 수 없습니다"));
        requireConversation(currentUser, assessment.getConversationId());
        return assessment;
    }

    private ChatDto.Result readResult(Assessment assessment) {
        if (assessment.getResult() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(assessment.getResult(), ChatDto.Result.class);
        } catch (JsonProcessingException e) {
            log.error("판정 결과 역직렬화 실패: {}", assessment.getRequestId(), e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 명세 예시(req_001)에 맞춘 형식. 자릿수를 넘으면 그대로 늘어난다. */
    private String nextRequestId() {
        long sequence = assessmentRepository.count() + 1;
        String candidate = "req_%03d".formatted(sequence);
        while (assessmentRepository.existsById(candidate)) {
            sequence++;
            candidate = "req_%03d".formatted(sequence);
        }
        return candidate;
    }
}
