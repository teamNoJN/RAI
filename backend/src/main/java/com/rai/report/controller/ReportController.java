package com.rai.report.controller;

import com.rai.report.dto.ReportDto;
import com.rai.report.entity.Report;
import com.rai.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 보고서 초안 생성 (screen-04, screen-05). */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportDto.CreateResponse> create(@Valid @RequestBody ReportDto.CreateRequest request) {
        Report report = reportService.create(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ReportDto.CreateResponse.from(report));
    }
}
