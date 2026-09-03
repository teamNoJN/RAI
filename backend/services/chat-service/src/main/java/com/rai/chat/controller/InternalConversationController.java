package com.rai.chat.controller;

import com.rai.chat.service.ConversationService;
import com.rai.common.security.AuthHeaders;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** 서비스 간 내부 API. Gateway 라우팅에 /internal/** 을 넣지 않아 외부에서 닿지 않는다. */
@Hidden
@RestController
@RequestMapping("/internal/conversations")
@RequiredArgsConstructor
public class InternalConversationController {

    private final ConversationService conversationService;

    /** drug-service 의 재검토 필요 배지용. */
    @GetMapping("/prior-countries")
    public List<String> priorCountries(@RequestHeader(AuthHeaders.COMPANY_ID) UUID companyId,
                                       @RequestParam("drug_id") UUID drugId) {
        return conversationService.priorCountries(companyId, drugId);
    }
}
