package io.github.derrickmunyole.loandecisioning.decisioning;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shape serialized into {@link UnderwritingSnapshot#getFactsJson()} — the declared
 * application-version fields plus the verification evidence available at {@code UNDERWRITING}
 * time (blueprint §5: "normalized facts JSON, evidence references"). Internal to {@code
 * decisioning}; nothing outside this module reads a snapshot yet (Milestone 3's later epics will
 * extract a read port when they need one, per ADR 0007's pattern).
 */
record UnderwritingFacts(
        BigDecimal requestedAmountKes,
        int requestedTermMonths,
        BigDecimal declaredMonthlyIncomeKes,
        String declaredEmploymentStatus,
        String declaredEmployerName,
        String loanPurpose,
        List<EvidenceItem> evidence) {

    record EvidenceItem(String type, String status, String provider, String detail) {}
}
