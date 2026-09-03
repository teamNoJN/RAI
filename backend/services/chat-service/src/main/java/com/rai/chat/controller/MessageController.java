package com.rai.chat.controller;

import com.rai.chat.dto.ChatDto;
import com.rai.chat.service.ChatService;
import com.rai.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** 3번 채팅 워크스페이스 (docs/api-spec/screen-03-chat.md). */
@Tag(name = "Chat", description = "채팅 메시지 · 판정")
@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final ChatService chatService;

    /**
     * 전송 버튼 · 퀵 액션 칩 공용. 판정을 뒤에서 처리하고 202 로 먼저 답한다 —
     * FE 는 request_id 로 2초 간격 폴링, 30초 초과 시 3E.
     */
    @Operation(summary = "메시지 전송", description = "202 · 400(처리 중 중복) · 404")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChatDto.AssessmentResponse send(CurrentUser currentUser,
                                           @PathVariable UUID conversationId,
                                           @Valid @RequestBody ChatDto.MessageRequest request) {
        return chatService.sendMessage(currentUser, conversationId, request);
    }

    @Operation(summary = "타임라인 복원", description = "200 · 404 — 3N 후속 알림 메시지 포함")
    @GetMapping
    public List<ChatDto.MessageResponse> list(CurrentUser currentUser,
                                              @PathVariable UUID conversationId) {
        return chatService.listMessages(currentUser, conversationId);
    }
}
