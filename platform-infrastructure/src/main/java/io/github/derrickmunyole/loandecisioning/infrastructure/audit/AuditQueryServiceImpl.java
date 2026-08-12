package io.github.derrickmunyole.loandecisioning.infrastructure.audit;

import io.github.derrickmunyole.loandecisioning.infrastructure.api.AuditEventView;
import io.github.derrickmunyole.loandecisioning.infrastructure.api.AuditQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class AuditQueryServiceImpl implements AuditQueryService {

    private final AuditEventRepository auditEventRepository;

    AuditQueryServiceImpl(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public List<AuditEventView> findByTarget(String targetType, String targetId) {
        return auditEventRepository
                .findByTargetTypeAndTargetIdOrderByOccurredAtAsc(targetType, targetId)
                .stream()
                .map(
                        event ->
                                new AuditEventView(
                                        event.getActor(),
                                        event.getAction(),
                                        event.getTargetType(),
                                        event.getTargetId(),
                                        event.getOccurredAt()))
                .toList();
    }
}
