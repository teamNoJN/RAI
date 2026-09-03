package com.rai.regulation.controller;

import com.rai.regulation.dto.RegulationSourceDto;
import com.rai.regulation.service.RegulationService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 서비스 간 내부 API. chat-service 가 판정 근거로 쓸 규제 문서를 가져간다.
 * 지금은 이 모놀리식 앱이 규제 KB 를 갖고 있고, ai-service 가 분리되면 URL 만 바뀐다.
 */
@Hidden
@RestController
@RequestMapping("/internal/regulations")
@RequiredArgsConstructor
public class InternalRegulationController {

    private final RegulationService regulationService;

    /** 해당 국가의 ACTIVE 규제 근거. 없으면 빈 배열 → 호출측이 3R 가드레일로 분기한다. */
    @GetMapping
    public List<RegulationSourceDto> sources(@RequestParam("country_id") String countryId) {
        return regulationService.findSources(countryId);
    }
}
