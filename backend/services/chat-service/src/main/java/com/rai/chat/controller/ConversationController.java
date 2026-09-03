package com.rai.chat.controller;

import com.rai.chat.dto.ConversationDto;
import com.rai.chat.service.ConversationService;
import com.rai.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 2번 대시보드 → 3번 채팅 진입 (docs/api-spec/screen-02-dashboard.md). */
@Tag(name = "Conversation", description = "대화 세션")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Validated
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "세션 생성", description = "201 · 400(국가 미선택) · 404(제품 없음)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto.CreateResponse create(CurrentUser currentUser,
                                                 @Valid @RequestBody ConversationDto.CreateRequest request) {
        return conversationService.create(currentUser, request);
    }

    @Operation(summary = "최근 대화 목록", description = "200 — 레일 하단")
    @GetMapping
    public List<ConversationDto.RecentResponse> recent(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        return conversationService.recent(currentUser, limit);
    }
}
