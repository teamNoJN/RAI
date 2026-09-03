package com.rai.regulation.controller;

import com.rai.common.security.CurrentUser;
import com.rai.regulation.dto.RegulationReviewDto;
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
 * 인증은 CurrentUser 리졸버가 강제한다 — Gateway 헤더 또는 Bearer 토큰이 없으면 401.
 * 승인자(reflected_by)도 optional 헤더가 아니라 CurrentUser 에서 받아 null 이 될 수 없다.
 */
@RestController
@RequestMapping("/api/regulations")
@RequiredArgsConstructor
public class RegulationReviewController {

    private final RegulationReviewService reviewService;

    @GetMapping("/feed")
    public List<RegulationReviewDto.FeedItem> feed(
            CurrentUser currentUser,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String status) {
        return reviewService.listFeed(country, status);
    }

    @GetMapping("/{regulationId}")
    public RegulationReviewDto.Detail detail(CurrentUser currentUser,
                                             @PathVariable UUID regulationId) {
        return reviewService.getDetail(regulationId);
    }

    @PostMapping("/{regulationId}/review")
    public RegulationReviewDto.ReviewResponse review(
            CurrentUser currentUser,
            @PathVariable UUID regulationId,
            @Valid @RequestBody RegulationReviewDto.ReviewRequest request) {
        return reviewService.review(regulationId, request.getApproved(), currentUser.userId());
    }
}
