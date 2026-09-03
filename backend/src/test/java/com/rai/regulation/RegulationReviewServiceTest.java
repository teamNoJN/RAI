package com.rai.regulation;

import com.rai.regulation.dto.RegulationReviewDto;
import com.rai.regulation.entity.RegulationRevision;
import com.rai.regulation.exception.RegulationApiException;
import com.rai.regulation.repository.RegulationRevisionRepository;
import com.rai.regulation.service.RegulationReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * screen-06 검수 콘솔 — 승인은 반드시 명시적 사람 액션이며, 이미 반영된 항목은
 * 다시 반영되지 않아야 한다(감사 추적 신뢰성의 핵심).
 */
@ExtendWith(MockitoExtension.class)
class RegulationReviewServiceTest {

    @Mock RegulationRevisionRepository revisionRepository;
    @InjectMocks RegulationReviewService reviewService;

    @Test
    void 승인하면_REFLECTED로_전환되고_승인자_이름을_기록한다() {
        UUID regulationId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        RegulationRevision revision = pendingRevision(regulationId);

        when(revisionRepository.findById(regulationId)).thenReturn(Optional.of(revision));
        when(revisionRepository.findUserName(approverId)).thenReturn(Optional.of("박준호"));

        RegulationReviewDto.ReviewResponse response = reviewService.review(regulationId, true, approverId);

        assertThat(response.getReviewStatus()).isEqualTo("REFLECTED");
        assertThat(response.getReflectedBy()).isEqualTo("박준호");
        assertThat(response.getReflectedAt()).isNotNull();
        assertThat(revision.getReviewStatus()).isEqualTo("REFLECTED");
    }

    @Test
    void 이미_반영된_항목은_다시_승인하면_409() {
        UUID regulationId = UUID.randomUUID();
        RegulationRevision reflected = pendingRevision(regulationId);
        reflected.setReviewStatus("REFLECTED");

        when(revisionRepository.findById(regulationId)).thenReturn(Optional.of(reflected));

        assertThatThrownBy(() -> reviewService.review(regulationId, true, UUID.randomUUID()))
                .isInstanceOf(RegulationApiException.class)
                .hasMessageContaining("이미 지식베이스에 반영");
    }

    @Test
    void 존재하지_않는_규제는_404() {
        UUID regulationId = UUID.randomUUID();
        when(revisionRepository.findById(regulationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.review(regulationId, true, UUID.randomUUID()))
                .isInstanceOf(RegulationApiException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void approved가_false면_400() {
        UUID regulationId = UUID.randomUUID();

        assertThatThrownBy(() -> reviewService.review(regulationId, false, UUID.randomUUID()))
                .isInstanceOf(RegulationApiException.class);
    }

    private RegulationRevision pendingRevision(UUID regulationId) {
        return RegulationRevision.builder()
                .regulationId(regulationId)
                .countryId("VN")
                .regulationType("고시")
                .title("MFDS 고시 2026-45호 개정")
                .reviewStatus("PENDING")
                .build();
    }
}
