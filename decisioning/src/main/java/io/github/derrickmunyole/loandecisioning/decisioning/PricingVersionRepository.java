package io.github.derrickmunyole.loandecisioning.decisioning;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PricingVersionRepository extends JpaRepository<PricingVersion, UUID> {}
