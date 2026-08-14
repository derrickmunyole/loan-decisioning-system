package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record PricingVersionResponse(
        UUID id,
        VersionStatus status,
        Map<String, Object> aprTermRules,
        String checksum,
        Instant createdAt,
        Instant publishedAt) {

    static PricingVersionResponse from(PricingVersion version, ObjectMapper objectMapper) {
        return new PricingVersionResponse(
                version.getId(),
                version.getStatus(),
                readJson(version.getAprTermRulesJson(), objectMapper),
                version.getChecksum(),
                version.getCreatedAt(),
                version.getPublishedAt());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored APR/term rules JSON is not valid JSON: " + json, e);
        }
    }
}
