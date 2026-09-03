package com.rai.drug.controller;

import com.rai.drug.dto.CountryDto;
import com.rai.drug.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** [채팅 시작 ▾] 드롭다운 (screen-02). 마스터 데이터라 회사 격리가 없다. */
@Tag(name = "Country", description = "국가 마스터")
@RestController
@RequestMapping("/api/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;

    @Operation(summary = "국가 목록", description = "200 — 목록 외 선택 차단용")
    @GetMapping
    public List<CountryDto> list() {
        return countryService.list();
    }
}
