package com.rai.drug.controller;

import com.rai.drug.service.CountryService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 내부 API. 3C 국가 변경 시 chat-service 가 "목록 외 선택"을 막는 데 쓴다. */
@Hidden
@RestController
@RequestMapping("/internal/countries")
@RequiredArgsConstructor
public class InternalCountryController {

    private final CountryService countryService;

    @GetMapping("/{countryId}/exists")
    public boolean exists(@PathVariable String countryId) {
        return countryService.exists(countryId);
    }
}
