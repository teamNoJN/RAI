package com.rai.chat.service;

import com.rai.chat.client.DrugServiceClient;
import com.rai.chat.dto.ConversationDto;
import com.rai.chat.entity.Conversation;
import com.rai.chat.repository.ConversationRepository;
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

    /** 약+국가 컨텍스트를 고정해 세션을 만든다. 제품이 없거나 남의 것이면 404. */
    @Transactional
    public ConversationDto.CreateResponse create(CurrentUser currentUser, ConversationDto.CreateRequest request) {
        drugServiceClient.requireDrug(request.drugId(), currentUser.companyId());

        Conversation conversation = Conversation.builder()
                .companyId(currentUser.companyId())
                .userId(currentUser.userId())
                .drugId(request.drugId())
                .countryId(request.countryId())
                .build();
        return ConversationDto.CreateResponse.from(conversationRepository.saveAndFlush(conversation));
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
