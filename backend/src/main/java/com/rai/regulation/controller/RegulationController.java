package com.rai.regulation.controller;

import com.rai.common.ApiResponse;
import com.rai.parser.ParserService;
import com.rai.regulation.dto.RegulationDto;
import com.rai.regulation.service.RegulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 규제 KB 관리 (운영자용). */
@Slf4j
@RestController
@RequestMapping("/api/regulations")
@RequiredArgsConstructor
public class RegulationController {

    private final RegulationService regulationService;
    private final ParserService parserService;

    @GetMapping
    public ApiResponse<List<RegulationDto.DocumentResponse>> list(@RequestParam(required = false) String country) {
        return ApiResponse.success(regulationService.listDocuments(country));
    }

    /** 문서 파일 + Metadata 로 규제 문서를 파싱해 KB 에 적재한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> ingest(
            @RequestPart("file") MultipartFile file,
            @Valid @ModelAttribute RegulationDto.IngestRequest request) throws IOException {
        int chunks = parserService.ingest(request.toEntity(), file.getOriginalFilename(), file.getInputStream());
        return ApiResponse.success("규제 문서가 등록되었습니다",
                Map.of("documentId", request.getDocumentId(), "chunkCount", chunks));
    }
}
