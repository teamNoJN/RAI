package com.rai.regulation.service;

import com.rai.regulation.dto.RegulationReviewDto;
import com.rai.regulation.entity.RegulationRevision;
import com.rai.regulation.exception.RegulationApiException;
import com.rai.regulation.repository.RegulationRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** screen-06 규제 변경 검수 콘솔. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegulationReviewService {

    private final RegulationRevisionRepository revisionRepository;

    public List<RegulationReviewDto.FeedItem> listFeed(String countryId, String reviewStatus) {
        boolean hasCountry = countryId != null && !countryId.isBlank();
        boolean hasStatus = reviewStatus != null && !reviewStatus.isBlank();

        List<RegulationRevision> revisions;
        if (hasCountry && hasStatus) {
            revisions = revisionRepository.findByCountryIdAndReviewStatusOrderByCreatedAtDesc(countryId, reviewStatus);
        } else if (hasCountry) {
            revisions = revisionRepository.findByCountryIdOrderByCreatedAtDesc(countryId);
        } else if (hasStatus) {
            revisions = revisionRepository.findByReviewStatusOrderByCreatedAtDesc(reviewStatus);
        } else {
            revisions = revisionRepository.findAllByOrderByCreatedAtDesc();
        }
        return RegulationReviewDto.FeedItem.from(revisions);
    }

    public RegulationReviewDto.Detail getDetail(UUID regulationId) {
        RegulationRevision revision = findOrThrow(regulationId);
        return RegulationReviewDto.Detail.from(revision, this::resolveName);
    }

    /** 명시적 사람 액션으로만 REFLECTED 전환. 반영 시각·주체는 서버가 기록한다(감사 추적). */
    @Transactional
    public RegulationReviewDto.ReviewResponse review(UUID regulationId, boolean approved, UUID approverId) {
        if (!approved) {
            throw new RegulationApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "approved=false 는 지원하지 않습니다. 반려는 검수 콘솔의 별도 흐름이 필요합니다.");
        }
        RegulationRevision revision = findOrThrow(regulationId);
        if ("REFLECTED".equals(revision.getReviewStatus())) {
            throw RegulationApiException.conflict("이미 지식베이스에 반영된 규제입니다: " + regulationId);
        }

        revision.setReviewStatus("REFLECTED");
        revision.setReflectedAt(Instant.now());
        revision.setReflectedBy(approverId);

        return RegulationReviewDto.ReviewResponse.from(revision, resolveName(approverId));
    }

    private RegulationRevision findOrThrow(UUID regulationId) {
        return revisionRepository.findById(regulationId)
                .orElseThrow(() -> RegulationApiException.notFound("규제 항목을 찾을 수 없습니다: " + regulationId));
    }

    private String resolveName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return revisionRepository.findUserName(userId).orElse(null);
    }
}
