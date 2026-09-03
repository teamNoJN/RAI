package com.rai.chat.service;

import com.rai.chat.client.DrugServiceClient;
import com.rai.chat.dto.ConversationDto;
import com.rai.chat.entity.Conversation;
import com.rai.chat.repository.ConversationRepository;
import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import com.rai.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** 2번 대시보드의 세션 시작 · 최근 대화 (docs/api-spec/screen-02-dashboard.md). */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final DrugServiceClient drugServiceClient;

    /** 약+국가 컨텍스트를 고정해 세션을 만든다. 제품이 없거나 남의 것이면 404, 없는 국가도 404. */
    @Transactional
    public ConversationDto.CreateResponse create(CurrentUser currentUser, ConversationDto.CreateRequest request) {
        drugServiceClient.requireDrug(request.drugId(), currentUser.companyId());
        // changeContext 와 같은 검증 — FK 위반 500 대신 계약된 에러로 낸다.
        if (!drugServiceClient.countryExists(request.countryId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "국가를 찾을 수 없습니다: " + request.countryId());
        }

        Conversation conversation = Conversation.builder()
                .companyId(currentUser.companyId())
                .userId(currentUser.userId())
                .drugId(request.drugId())
                .countryId(request.countryId())
                .build();
        return ConversationDto.CreateResponse.from(conversationRepository.saveAndFlush(conversation));
    }

    /**
     * 세션 단건 조회 — 최근 목록(limit 5) 밖의 세션을 열어도 FE 가 약·국가 컨텍스트를
     * 복원할 수 있어야 한다. 다른 회사 것은 존재해도 404.
     */
    @Transactional(readOnly = true)
    public ConversationDto.CreateResponse get(CurrentUser currentUser, UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(c -> c.getCompanyId().equals(currentUser.companyId()))
                .map(ConversationDto.CreateResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "대화를 찾을 수 없습니다"));
    }

    /** 레일 최근 대화. product_name 은 drug-service 에서 배치로 채운다. */
    @Transactional(readOnly = true)
    public List<ConversationDto.RecentResponse> recent(CurrentUser currentUser, int limit) {
        List<Conversation> conversations = conversationRepository.findRecent(
                currentUser.companyId(), currentUser.userId(), PageRequest.of(0, limit));

        Function<UUID, String> productName = drugServiceClient.nameLookup(
                conversations.stream().map(Conversation::getDrugId).distinct().toList(),
                currentUser.companyId());

        return conversations.stream()
                .map(c -> ConversationDto.RecentResponse.of(c, productName.apply(c.getDrugId())))
                .toList();
    }

    /** drug-service 재검토 배지용 — 이 제품으로 대화한 국가들. */
    @Transactional(readOnly = true)
    public List<String> priorCountries(UUID companyId, UUID drugId) {
        return conversationRepository.findPriorCountries(companyId, drugId);
    }
}
