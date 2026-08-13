package io.github.derrickmunyole.loandecisioning.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces ADR 0001's cross-module rule: a bounded-context module's internals are private, reachable
 * from other modules only through its {@code xxx.api} package. Scoped to {@code infrastructure},
 * {@code origination}, {@code workflow}, and {@code verification} (and, as future milestones add
 * them, {@code decisioning}/etc.) — {@code common} (shared kernel: value types, base entity types, exception
 * hierarchy meant to be used everywhere) and {@code security} (wired by the Spring framework itself via
 * filter chain/annotations, not imported directly by other bounded contexts) are deliberately exempt as
 * targets. They're still bound as callers: reaching from either of them into another module's internals
 * would still fail the rules below.
 */
@AnalyzeClasses(
        packages = "io.github.derrickmunyole.loandecisioning",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule infrastructure_internals_are_only_reachable_through_its_api =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..infrastructure..")
                    .should()
                    .dependOnClassesThat(
                            resideInAPackage("..infrastructure..")
                                    .and(not(resideInAPackage("..infrastructure.api.."))));

    @ArchTest
    static final ArchRule origination_internals_are_only_reachable_through_its_api =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..origination..")
                    .should()
                    .dependOnClassesThat(
                            resideInAPackage("..origination..")
                                    .and(not(resideInAPackage("..origination.api.."))));

    @ArchTest
    static final ArchRule workflow_internals_are_only_reachable_through_its_api =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..workflow..")
                    .should()
                    .dependOnClassesThat(
                            resideInAPackage("..workflow..")
                                    .and(not(resideInAPackage("..workflow.api.."))));

    @ArchTest
    static final ArchRule verification_internals_are_only_reachable_through_its_api =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..verification..")
                    .should()
                    .dependOnClassesThat(
                            resideInAPackage("..verification..")
                                    .and(not(resideInAPackage("..verification.api.."))));
}
