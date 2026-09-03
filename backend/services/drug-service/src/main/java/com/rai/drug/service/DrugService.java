package com.rai.drug.service;

import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import com.rai.drug.client.ChatServiceClient;
import com.rai.drug.dto.DrugDto;
import com.rai.drug.entity.Drug;
import com.rai.drug.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 2 · 2E · 2F · 2S 화면 (docs/api-spec/screen-02*.md). */
@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugRepository drugRepository;
    private final ChatServiceClient chatServiceClient;

    /** 대시보드 제품 카드. q 가 있으면 2S 검색(제품명·성분). 결과 없으면 빈 배열(2E/2S). */
    @Transactional(readOnly = true)
    public List<DrugDto.DrugResponse> list(UUID companyId, String keyword) {
        List<Drug> drugs = (keyword == null || keyword.isBlank())
                ? drugRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                : drugRepository.search(companyId, keyword.trim());
        return drugs.stream().map(DrugDto.DrugResponse::from).toList();
    }

    /** 2F 제품 등록. version 은 1 로 시작하고, 수정 시 덮어쓰지 않고 증가시킨다. 같은 이름은 409. */
    @Transactional
    public DrugDto.CreateResponse create(UUID companyId, DrugDto.CreateRequest request) {
        String productName = request.productName().trim();
        if (drugRepository.existsByCompanyIdAndProductName(companyId, productName)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "이미 등록된 제품명입니다: " + productName);
        }
        Drug drug = Drug.builder()
                .companyId(companyId)
                .productName(request.productName().trim())
                .ingredients(request.ingredients().stream().map(String::trim).toList())
                .strength(request.strength())
                .dosageForm(request.dosageForm())
                .build();
        return DrugDto.CreateResponse.from(drugRepository.saveAndFlush(drug));
    }

    /** 재검토 필요 배지. 이 제품으로 대화한 이력이 있으면 needed=true. */
    @Transactional(readOnly = true)
    public DrugDto.ReassessmentResponse reassessmentNeeded(UUID companyId, UUID drugId) {
        requireOwnedDrug(companyId, drugId);
        return DrugDto.ReassessmentResponse.of(chatServiceClient.priorCountries(drugId, companyId));
    }

    /** chat-service 가 세션을 만들기 전에 제품 존재·소유를 확인한다. */
    @Transactional(readOnly = true)
    public DrugDto.InternalDrugResponse internalGet(UUID companyId, UUID drugId) {
        return DrugDto.InternalDrugResponse.from(requireOwnedDrug(companyId, drugId));
    }

    /** 최근 대화 목록에 제품명을 채우기 위한 배치 조회. */
    @Transactional(readOnly = true)
    public List<DrugDto.InternalDrugResponse> internalGetAll(UUID companyId, List<UUID> drugIds) {
        if (drugIds.isEmpty()) {
            return List.of();
        }
        return drugRepository.findByCompanyIdAndDrugIdIn(companyId, drugIds).stream()
                .map(DrugDto.InternalDrugResponse::from)
                .toList();
    }

    /** 다른 회사 제품은 존재해도 404 로 가린다(존재 여부 노출 방지). */
    private Drug requireOwnedDrug(UUID companyId, UUID drugId) {
        return drugRepository.findByDrugIdAndCompanyId(drugId, companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "제품을 찾을 수 없습니다"));
    }
}
