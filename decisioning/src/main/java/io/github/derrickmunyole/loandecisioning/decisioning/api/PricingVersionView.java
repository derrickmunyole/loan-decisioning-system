package io.github.derrickmunyole.loandecisioning.decisioning.api;

import java.util.UUID;

/**
 * Read-only view of a {@code PricingVersion} row, exposed for {@code offers} (Epic 5.1) — the
 * first external caller needing pricing data, per the same "extract a port on the second real
 * caller" pattern as {@code DecisionView}. {@code aprTermRulesJson} is left raw rather than
 * parsed at the boundary: how to interpret it is {@code offers}'s own {@code PricingEvaluator}
 * concern, the same reason {@code ScorecardVersion.formulaConfigJson}/{@code
 * PolicyVersion.rulesJson} are parsed by their one consumer (inside {@code decisioning} itself)
 * rather than at a port.
 */
public record PricingVersionView(UUID id, String aprTermRulesJson) {}