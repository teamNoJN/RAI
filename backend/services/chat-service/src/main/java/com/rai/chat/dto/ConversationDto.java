package com.rai.chat.dto;

import com.rai.chat.entity.Conversation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** 2번 대시보드에서 세션을 시작·이어하기 (docs/api-spec/screen-02-dashboard.md). */
public final class ConversationDto {

    private ConversationDto() {}

    /** [채팅 시작 ▾] → 국가 선택. 국가 미선택이면 400. */
    public record CreateRequest(
            @NotNull(message = "제품을 선택해주세요")
            UUID drugId,

            @NotBlank(message = "국가를 선택해주세요")
            String countryId
    ) {}

    /** 세션 생성 201 → 3번 채팅 화면으로 이동. */
    public record CreateResponse(String conversationId, String drugId, String countryId, Instant createdAt) {

        public static CreateResponse from(Conversation conversation) {
            return new CreateResponse(
                    conversation.getConversationId().toString(),
                    conversation.getDrugId().toString(),
                    conversation.getCountryId(),
                    conversation.getCreatedAt());
        }
    }

    /** 레일 최근 대화 목록. product_name 은 drug-service 에서 채워온다. */
    public record RecentResponse(String conversationId, String productName,
                                 String countryId, Instant lastMessageAt) {

        public static RecentResponse of(Conversation conversation, String productName) {
            return new RecentResponse(
                    conversation.getConversationId().toString(),
                    productName,
                    conversation.getCountryId(),
                    conversation.getLastMessageAt());
        }
    }
}
