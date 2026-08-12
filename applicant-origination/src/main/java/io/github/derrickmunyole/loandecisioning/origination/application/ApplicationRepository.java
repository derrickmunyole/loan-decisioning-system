package io.github.derrickmunyole.loandecisioning.origination.application;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {}
