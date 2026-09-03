package com.rai.drug.controller;

import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import com.rai.common.security.AuthHeaders;
import com.rai.drug.dto.DrugDto;
import com.rai.drug.service.DrugService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 서비스 간 내부 API. Gateway 라우팅 규칙에 /internal/** 을 넣지 않으므로
 * 외부에서는 닿지 않는다 (docs/00-project-plan.md ③).
 */
@Hidden
@RestController
@RequestMapping("/internal/drugs")
@RequiredArgsConstructor
public class InternalDrugController {

    private final DrugService drugService;

    /** chat-service 가 세션 생성 전 제품 존재·소유를 확인한다. 없으면 404. */
    @GetMapping("/{drugId}")
    public DrugDto.InternalDrugResponse get(@RequestHeader(AuthHeaders.COMPANY_ID) UUID companyId,
                                            @PathVariable UUID drugId) {
        return drugService.internalGet(companyId, drugId);
    }

    /** 최근 대화 목록의 product_name 을 채우기 위한 배치 조회. */
    @GetMapping
    public List<DrugDto.InternalDrugResponse> getAll(@RequestHeader(AuthHeaders.COMPANY_ID) UUID companyId,
                                                     @RequestParam("ids") String ids) {
        return drugService.internalGetAll(companyId, parseIds(ids));
    }

    private List<UUID> parseIds(String ids) {
        try {
            return Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .map(UUID::fromString)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "drug_id 형식이 올바르지 않습니다");
        }
    }
}
