package com.rai.drug.controller;

import com.rai.common.security.CurrentUser;
import com.rai.drug.dto.DrugDto;
import com.rai.drug.service.DrugService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** 2 · 2E · 2F · 2S 화면 (docs/api-spec/screen-02*.md). */
@Tag(name = "Drug", description = "제품 대시보드 · 등록 · 검색")
@RestController
@RequestMapping("/api/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    @Operation(summary = "제품 목록 / 검색", description = "200 · q 있으면 제품명·성분 검색(2S) · 빈 배열이면 2E")
    @GetMapping
    public List<DrugDto.DrugResponse> list(CurrentUser currentUser,
                                           @RequestParam(required = false) String q) {
        return drugService.list(currentUser.companyId(), q);
    }

    @Operation(summary = "제품 등록", description = "201 · 400 VALIDATION_ERROR")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DrugDto.CreateResponse create(CurrentUser currentUser,
                                         @Valid @RequestBody DrugDto.CreateRequest request) {
        return drugService.create(currentUser.companyId(), request);
    }

    @Operation(summary = "재검토 필요 여부", description = "200 · 404")
    @GetMapping("/{drugId}/reassessment-needed")
    public DrugDto.ReassessmentResponse reassessmentNeeded(CurrentUser currentUser,
                                                           @PathVariable UUID drugId) {
        return drugService.reassessmentNeeded(currentUser.companyId(), drugId);
    }
}
