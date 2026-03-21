package com.pesaloop.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Domain model purity tests.
 *
 * Domain models must be pure Java. They may only depend on:
 *   - Other domain models within the same or shared contexts
 *   - Java standard library
 *   - Lombok (compile-time only, no runtime footprint)
 *
 * They must never depend on:
 *   - Spring framework
 *   - JPA / Hibernate
 *   - Jackson (JSON)
 *   - JDBC
 *   - Any external library with runtime coupling
 */
@AnalyzeClasses(
        packages = "com.pesaloop",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class
)
public class DomainModelPurityTest {

    private static final String ROOT = "com.pesaloop";

    // ── No Jackson on domain models ───────────────────────────────────────────

    @ArchTest
    static final ArchRule domain_models_have_no_jackson_annotations =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..domain.model..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.fasterxml.jackson..")
                    .because("Domain models are serialisation-agnostic. JSON concerns belong " +
                            "in DTOs in the application layer or in adapter request/response objects.");

    // ── No Lombok @Data on aggregates (use @Getter + @Builder) ───────────────

    @ArchTest
    static final ArchRule domain_aggregates_do_not_use_lombok_data =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..domain.model..")
                    .and().haveSimpleNameNotEndingWith("Config")  // value objects may use @Data
                    .and().haveSimpleNameNotEndingWith("Status")  // enums are fine
                    .and().haveSimpleNameNotEndingWith("Role")
                    .and().haveSimpleNameNotEndingWith("Type")
                    .and().haveSimpleNameNotEndingWith("Frequency")
                    .should().beAnnotatedWith("lombok.Data")
                    .because("@Data generates equals/hashCode based on all fields, which breaks " +
                            "aggregate identity semantics. Use @Getter + @Builder on aggregates " +
                            "and implement equals/hashCode based on the identity field (id) only.");

    // ── No @Transactional in domain ───────────────────────────────────────────

    @ArchTest
    static final ArchRule domain_has_no_transactional_annotations =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..domain..")
                    .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                    .because("Transaction management is an infrastructure concern. Domain logic " +
                            "must be transaction-agnostic. @Transactional belongs on use cases " +
                            "in the application layer.");

    // ── No static Spring context access in domain ─────────────────────────────

    @ArchTest
    static final ArchRule domain_does_not_access_spring_context =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..domain..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.context.ApplicationContext")
                    .because("Domain classes must never access the Spring application context. " +
                            "Dependencies are injected by the container, never pulled.");

    // ── Value objects in domain are immutable (no setter methods) ────────────

    @ArchTest
    static final ArchRule shared_domain_value_objects_have_no_setters =
            noMethods()
                    .that().haveNameStartingWith("set")
                    .and().areDeclaredInClassesThat()
                    .resideInAPackage(ROOT + ".shared.domain..")
                    .should().beDeclaredInClassesThat()
                    .resideInAPackage(ROOT + ".shared.domain..")
                    .because("Shared kernel value objects (Money, TenantContext) must be " +
                            "immutable. Setters break value object semantics.");

    // ── Domain ports declare no state (fields) ────────────────────────────────

    @ArchTest
    static final ArchRule domain_ports_have_no_instance_fields =
            noFields()
                    .that().areDeclaredInClassesThat()
                    .resideInAPackage(ROOT + ".(*)..domain.port..")
                    .and().areDeclaredInClassesThat().areInterfaces()
                    .should().beDeclaredInClassesThat().areInterfaces()
                    .because("Port interfaces define contracts, not state. " +
                            "Instance fields on interfaces would be implicitly public static final, " +
                            "which creates hidden coupling.");

    // ── No domain model depends on another context's domain model directly ────

    @ArchTest
    static final ArchRule loan_domain_models_do_not_depend_on_contribution_domain_models =
            noClasses()
                    .that().resideInAPackage(ROOT + ".loan.domain.model..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".contribution.domain.model..")
                    .because("Domain model classes in different bounded contexts must not reference " +
                            "each other directly. Cross-context relationships use IDs (UUID), " +
                            "not direct object references.");

    @ArchTest
    static final ArchRule contribution_domain_models_do_not_depend_on_loan_domain_models =
            noClasses()
                    .that().resideInAPackage(ROOT + ".contribution.domain.model..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".loan.domain.model..")
                    .because("Domain models in different bounded contexts must not reference " +
                            "each other. They communicate through IDs and domain events.");

    // ── Domain services contain only domain logic ─────────────────────────────

    @ArchTest
    static final ArchRule domain_services_do_not_depend_on_repositories =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..domain.service..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".(*)..adapters.persistence..")
                    .because("Domain services contain pure business calculations. They must not " +
                            "access persistence adapters — that would make them application services. " +
                            "Use application/usecase for orchestration with repositories.");
}