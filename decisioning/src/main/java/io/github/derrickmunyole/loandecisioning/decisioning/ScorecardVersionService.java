package io.github.derrickmunyole.loandecisioning.decisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.derrickmunyole.loandecisioning.common.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScorecardVersionService {

    private final ScorecardVersionRepository scorecardVersionRepository;
    private final ObjectMapper objectMapper;

    ScorecardVersionService(
            ScorecardVersionRepository scorecardVersionRepository, ObjectMapper objectMapper) {
        this.scorecardVersionRepository = scorecardVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    ScorecardVersionResponse create(CreateScorecardVersionRequest request) {
        String formulaConfigJson = writeJson(request.formulaConfig());
        String checksum = Sha256.hex(formulaConfigJson.getBytes(StandardCharsets.UTF_8));
        ScorecardVersion version =
                scorecardVersionRepository.save(new ScorecardVersion(formulaConfigJson, checksum));
        return ScorecardVersionResponse.from(version, objectMapper);
    }

    @Transactional
    ScorecardVersionResponse publish(UUID id) {
        ScorecardVersion version =
                scorecardVersionRepository
                        .findById(id)
                        .orElseThrow(() -> new VersionNotFoundException("ScorecardVersion", id));
        version.publish();
        return ScorecardVersionResponse.from(version, objectMapper);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize scorecard formula config: " + value, e);
        }
    }
}
