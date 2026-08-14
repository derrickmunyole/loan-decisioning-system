package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record ScorecardVersionResponse(
        UUID id,
        VersionStatus status,
        Map<String, Object> formulaConfig,
        String checksum,
        Instant createdAt,
        Instant publishedAt) {

    static ScorecardVersionResponse from(ScorecardVersion version, ObjectMapper objectMapper) {
        return new ScorecardVersionResponse(
                version.getId(),
                version.getStatus(),
                readJson(version.getFormulaConfigJson(), objectMapper),
                version.getChecksum(),
                version.getCreatedAt(),
                version.getPublishedAt());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored formula config JSON is not valid JSON: " + json, e);
        }
    }
}
