package io.github.derrickmunyole.loandecisioning.decisioning.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.decisioning.Decision;
import io.github.derrickmunyole.loandecisioning.decisioning.DecisionRepository;
import io.github.derrickmunyole.loandecisioning.decisioning.UnderwritingSnapshotRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only access to {@code Decision} rows for modules outside {@code decisioning} — the first
 * external read port off {@code Decision}, extracted for Epic 4.3's aggregated {@code
 * GET /applications/{id}/timeline}, the same "extract a port on the second real caller" pattern
 * ADR 0007 documents. {@link #findById} and {@link DecisionView#applicationVersionId()} were
 * added for Epic 5.1's {@code offers} listener, which needs the exact {@code ApplicationVersion}
 * a {@code Decision} was computed against to read the requested loan amount — resolved via the
 * {@code UnderwritingSnapshot} the decision already references, rather than guessing at an
 * application's "latest" version, which would silently break the day resubmission/versioning
 * becomes real.
 */
@Service
public class DecisionQueryService {

    private final DecisionRepository decisionRepository;
    private final UnderwritingSnapshotRepository underwritingSnapshotRepository;
    private final ObjectMapper objectMapper;

    public DecisionQueryService(
            DecisionRepository decisionRepository,
            UnderwritingSnapshotRepository underwritingSnapshotRepository,
            ObjectMapper objectMapper) {
        this.decisionRepository = decisionRepository;
        this.underwritingSnapshotRepository = underwritingSnapshotRepository;
        this.objectMapper = objectMapper;
    }

    public List<DecisionView> findByApplicationId(UUID applicationId) {
        return decisionRepository.findByApplicationIdOrderByDecidedAtAsc(applicationId).stream()
                .map(this::toView)
                .toList();
    }

    public Optional<DecisionView> findById(UUID decisionId) {
        return decisionRepository.findById(decisionId).map(this::toView);
    }

    private DecisionView toView(Decision decision) {
        UUID applicationVersionId =
                underwritingSnapshotRepository
                        .findById(decision.getUnderwritingSnapshotId())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No UnderwritingSnapshot " + decision.getUnderwritingSnapshotId()))
                        .getApplicationVersionId();
        return new DecisionView(
                decision.getId(),
                decision.getApplicationId(),
                decision.getUnderwritingSnapshotId(),
                applicationVersionId,
                decision.getPolicyVersionId(),
                decision.getScorecardVersionId(),
                decision.getPricingVersionId(),
                decision.getCreditScoreModelVersion(),
                decision.getOutcome(),
                readReasons(decision.getReasonCodesJson()),
                decision.getActor(),
                decision.getDecidedAt(),
                decision.getOverridesDecisionId());
    }

    private List<String> readReasons(String reasonCodesJson) {
        try {
            return objectMapper.readValue(reasonCodesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Stored reason codes JSON is not valid JSON: " + reasonCodesJson, e);
        }
    }
}
