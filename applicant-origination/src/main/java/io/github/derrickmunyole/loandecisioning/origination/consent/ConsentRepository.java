package io.github.derrickmunyole.loandecisioning.origination.consent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    List<Consent> findByApplicationVersionId(UUID applicationVersionId);
}
