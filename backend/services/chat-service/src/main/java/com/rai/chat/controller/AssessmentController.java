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

/** 판정 폴링(3 · 3E) 과 판정 카드 피드백. */
@Tag(name = "Assessment", description = "판정 조회 · 피드백")
@RestController
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final ChatService chatService;

    @Operation(summary = "판정 조회", description = "200 · 404 — failed 면 status 만 (3E)")
    @GetMapping("/{requestId}")
    public ChatDto.AssessmentResponse get(CurrentUser currentUser, @PathVariable String requestId) {
        return chatService.getAssessment(currentUser, requestId);
    }

    @Operation(summary = "판정 피드백", description = "201 · 404 — 👍 유용 / ✎ 수정 필요")
    @PostMapping("/{requestId}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDto.FeedbackResponse feedback(CurrentUser currentUser,
                                             @PathVariable String requestId,
                                             @Valid @RequestBody ChatDto.FeedbackRequest request) {
        return chatService.recordFeedback(currentUser, requestId, request);
    }
}
