package io.github.derrickmunyole.loandecisioning.decisioning.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.decisioning.Decision;
import io.github.derrickmunyole.loandecisioning.decisioning.DecisionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only access to {@code Decision} rows for modules outside {@code decisioning} — the first
 * external read port off {@code Decision}, extracted for Epic 4.3's aggregated {@code
 * GET /applications/{id}/timeline}, the same "extract a port on the second real caller" pattern
 * ADR 0007 documents.
 */
@Service
public class DecisionQueryService {

    private final DecisionRepository decisionRepository;
    private final ObjectMapper objectMapper;

    public DecisionQueryService(DecisionRepository decisionRepository, ObjectMapper objectMapper) {
        this.decisionRepository = decisionRepository;
        this.objectMapper = objectMapper;
    }

    public List<DecisionView> findByApplicationId(UUID applicationId) {
        return decisionRepository.findByApplicationIdOrderByDecidedAtAsc(applicationId).stream()
                .map(this::toView)
                .toList();
    }

    private DecisionView toView(Decision decision) {
        return new DecisionView(
                decision.getId(),
                decision.getApplicationId(),
                decision.getUnderwritingSnapshotId(),
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
