package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

record PolicyVersionResponse(
        UUID id,
        VersionStatus status,
        LocalDate effectiveDate,
        Map<String, Object> rules,
        String checksum,
        Instant createdAt,
        Instant publishedAt) {

    static PolicyVersionResponse from(PolicyVersion version, ObjectMapper objectMapper) {
        return new PolicyVersionResponse(
                version.getId(),
                version.getStatus(),
                version.getEffectiveDate(),
                readJson(version.getRulesJson(), objectMapper),
                version.getChecksum(),
                version.getCreatedAt(),
                version.getPublishedAt());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored rules JSON is not valid JSON: " + json, e);
        }
    }
}
