package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.common.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PolicyVersionService {

    private final PolicyVersionRepository policyVersionRepository;
    private final ObjectMapper objectMapper;

    PolicyVersionService(PolicyVersionRepository policyVersionRepository, ObjectMapper objectMapper) {
        this.policyVersionRepository = policyVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    PolicyVersionResponse create(CreatePolicyVersionRequest request) {
        String rulesJson = writeJson(request.rules());
        String checksum = Sha256.hex(rulesJson.getBytes(StandardCharsets.UTF_8));
        PolicyVersion version =
                policyVersionRepository.save(
                        new PolicyVersion(request.effectiveDate(), rulesJson, checksum));
        return PolicyVersionResponse.from(version, objectMapper);
    }

    @Transactional
    PolicyVersionResponse publish(UUID id) {
        PolicyVersion version =
                policyVersionRepository
                        .findById(id)
                        .orElseThrow(() -> new VersionNotFoundException("PolicyVersion", id));
        version.publish();
        return PolicyVersionResponse.from(version, objectMapper);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize policy rules: " + value, e);
        }
    }
}
