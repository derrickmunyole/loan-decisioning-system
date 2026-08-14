package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.common.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PricingVersionService {

    private final PricingVersionRepository pricingVersionRepository;
    private final ObjectMapper objectMapper;

    PricingVersionService(
            PricingVersionRepository pricingVersionRepository, ObjectMapper objectMapper) {
        this.pricingVersionRepository = pricingVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    PricingVersionResponse create(CreatePricingVersionRequest request) {
        String aprTermRulesJson = writeJson(request.aprTermRules());
        String checksum = Sha256.hex(aprTermRulesJson.getBytes(StandardCharsets.UTF_8));
        PricingVersion version =
                pricingVersionRepository.save(new PricingVersion(aprTermRulesJson, checksum));
        return PricingVersionResponse.from(version, objectMapper);
    }

    @Transactional
    PricingVersionResponse publish(UUID id) {
        PricingVersion version =
                pricingVersionRepository
                        .findById(id)
                        .orElseThrow(() -> new VersionNotFoundException("PricingVersion", id));
        version.publish();
        return PricingVersionResponse.from(version, objectMapper);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize pricing APR/term rules: " + value, e);
        }
    }
}
