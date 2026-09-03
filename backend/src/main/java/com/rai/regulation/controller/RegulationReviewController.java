package com.rai.regulation.controller;

import com.rai.regulation.dto.RegulationReviewDto;
import com.rai.regulation.exception.RegulationApiException;
import com.rai.regulation.service.RegulationReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * screen-06 규제 변경 검수 콘솔. 승인은 반드시 사람 액션이며(자동 반영 금지),
 * 반영 시각·주체를 서버가 기록한다(감사 추적) — docs/api-spec/screen-06-review-console.md.
 *
 * 승인자 식별은 Gateway 가 JWT 검증 후 내려주는 X-User-Id 헤더를 신뢰한다
 * (docs/00-project-plan.md 4-5, common/security/AuthHeaders 와 동일 규약).
 * Gateway 앞단이 아직 없는 로컬 환경에서는 호출자가 직접 헤더를 실어 보내야 한다.
 */
@RestController
@RequestMapping("/api/regulations")
@RequiredArgsConstructor
public class RegulationReviewController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RegulationReviewService reviewService;

    @GetMapping("/feed")
    public List<RegulationReviewDto.FeedItem> feed(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String status) {
        return reviewService.listFeed(country, status);
    }

    @GetMapping("/{regulationId}")
    public RegulationReviewDto.Detail detail(@PathVariable UUID regulationId) {
        return reviewService.getDetail(regulationId);
    }

    @PostMapping("/{regulationId}/review")
    public RegulationReviewDto.ReviewResponse review(
            @PathVariable UUID regulationId,
            @Valid @RequestBody RegulationReviewDto.ReviewRequest request,
            @RequestHeader(value = USER_ID_HEADER, required = false) String userIdHeader) {
        UUID approverId = parseApprover(userIdHeader);
        return reviewService.review(regulationId, request.getApproved(), approverId);
    }

    private UUID parseApprover(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw RegulationApiException.unauthorized("로그인이 필요합니다");
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw RegulationApiException.unauthorized("사용자 식별 정보가 올바르지 않습니다");
        }
    }
}
