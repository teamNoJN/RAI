package com.rai.drug.dto;

import com.rai.drug.entity.Drug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 2번 대시보드 · 2F 등록 · 2S 검색 계약 (docs/api-spec/screen-02*.md). */
public final class DrugDto {

    private DrugDto() {}

    /** 2F 제품 등록 요청. product_name/ingredients 누락 시 400 VALIDATION_ERROR. */
    public record CreateRequest(
            @NotBlank(message = "제품명을 입력해주세요")
            @Size(max = 255, message = "제품명은 255자를 넘을 수 없습니다")
            String productName,

            @NotEmpty(message = "성분을 1개 이상 입력해주세요")
            List<String> ingredients,

            @Size(max = 100, message = "함량은 100자를 넘을 수 없습니다")
            String strength,

            @Size(max = 100, message = "제형은 100자를 넘을 수 없습니다")
            String dosageForm
    ) {}

    /** 2F 등록 성공 201. */
    public record CreateResponse(String drugId, String productName, Integer version) {

        public static CreateResponse from(Drug drug) {
            return new CreateResponse(drug.getDrugId().toString(), drug.getProductName(), drug.getVersion());
        }
    }

    /** 2번 제품 카드 · 2S 검색 결과 (동일 스키마). */
    public record DrugResponse(String drugId, String productName, List<String> ingredients,
                               String strength, String dosageForm, Integer version) {

        public static DrugResponse from(Drug drug) {
            return new DrugResponse(
                    drug.getDrugId().toString(),
                    drug.getProductName(),
                    drug.getIngredients(),
                    drug.getStrength(),
                    drug.getDosageForm(),
                    drug.getVersion());
        }
    }

    /** 재검토 필요 배지 (screen-02). prior_countries 는 chat-service 가 가진 대화 이력에서 온다. */
    public record ReassessmentResponse(boolean needed, List<String> priorCountries, String message) {

        private static final String NEEDED_MESSAGE = "기존 판정 결과가 존재합니다. 재검토가 필요할 수 있습니다.";

        public static ReassessmentResponse of(List<String> priorCountries) {
            boolean needed = !priorCountries.isEmpty();
            return new ReassessmentResponse(needed, priorCountries, needed ? NEEDED_MESSAGE : null);
        }
    }

    /** 서비스 간 내부 호출용 최소 정보 (/internal/drugs). 외부에 노출하지 않는다. */
    public record InternalDrugResponse(String drugId, String productName) {

        public static InternalDrugResponse from(Drug drug) {
            return new InternalDrugResponse(drug.getDrugId().toString(), drug.getProductName());
        }
    }
}
