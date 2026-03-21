package com.pesaloop.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Hexagonal / Ports-and-Adapters architecture rules — Cockburn Option B layout.
 *
 * Package structure per bounded context:
 *   domain/model/           — pure Java aggregates (no Spring, no JPA)
 *   domain/service/         — pure domain logic
 *   application/port/in/    — INPUT ports  (what the hexagon offers to driving adapters)
 *   application/port/out/   — OUTPUT ports (what the hexagon needs from infrastructure)
 *   application/usecase/    — implements port/in/ interfaces
 *   application/dto/        — request/response data shapes
 *   adapters/web/           — primary:   HTTP → port/in/
 *   adapters/scheduler/     — primary:   cron  → port/in/
 *   adapters/persistence/   — secondary: implements port/out/
 *   adapters/mpesa/         — secondary: implements MpesaGateway (port/out/)
 *   adapters/sms/           — secondary: implements SmsGateway (port/out/)
 *   adapters/security/      — cross-cutting JWT support
 */
@AnalyzeClasses(
        packages = "com.pesaloop",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class
)
public class HexagonalArchitectureTest {

    private static final String ROOT = "com.pesaloop";

    // ── 1. Core layered architecture ─────────────────────────────────────────

    @ArchTest
    static final ArchRule layered_architecture_is_respected =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain")     .definedBy(ROOT + ".(*)..domain..")
                    .layer("Application").definedBy(ROOT + ".(*)..application..")
                    .layer("Adapters")   .definedBy(ROOT + ".(*)..adapters..")
                    .layer("Config")     .definedBy(ROOT + ".config..")
                    .whereLayer("Domain")     .mayOnlyBeAccessedByLayers("Application", "Adapters", "Config")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
                    .whereLayer("Adapters")   .mayNotBeAccessedByAnyLayer()
                    .because("Cockburn hexagonal: domain ← application ← adapters. One-way dependency.");

    // ── 2. Domain is pure Java — no framework ─────────────────────────────────

    @ArchTest
    static final ArchRule domain_has_no_framework_dependencies =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
                    .because("Domain (model + service) is pure Java. No Spring, no JPA, no Hibernate.");

    // ── 3. Option B enforced: no domain/port package ──────────────────────────

    @ArchTest
    static final ArchRule no_domain_port_package_exists =
            noClasses()
                    .should().resideInAPackage(ROOT + ".(*)..domain.port..")
                    .because("Option B (Cockburn): all ports live under application/port/in/ or " +
                             "application/port/out/. The domain/ layer contains only model/ and service/.");

    // ── 4. Input ports are interfaces in application/port/in/ ─────────────────

    @ArchTest
    static final ArchRule input_ports_are_interfaces =
            classes()
                    .that().resideInAPackage(ROOT + ".(*)..application.port.in..")
                    .and().areNotRecords()
                    .should().beInterfaces()
                    .because("Input ports define what the hexagon offers to driving adapters (HTTP, schedulers). " +
                             "Interfaces allow adapter isolation and easy test doubles.");

    // ── 5. Output ports are interfaces in application/port/out/ ───────────────

    @ArchTest
    static final ArchRule output_ports_are_interfaces =
            classes()
                    .that().resideInAPackage(ROOT + ".(*)..application.port.out..")
                    .and().areNotRecords()
                    .and().areNotEnums()
                    .should().beInterfaces()
                    .because("Output ports define what the hexagon needs from infrastructure. " +
                             "Interfaces decouple the application from JPA, JDBC, and external APIs.");

    // ── 6. Use cases implement input ports ────────────────────────────────────

    @ArchTest
    static final ArchRule use_cases_live_in_application_usecase =
            classes()
                    .that().haveSimpleNameEndingWith("UseCase")
                    .and().areNotInterfaces()
                    .should().resideInAPackage(ROOT + ".(*)..application.usecase..")
                    .because("Concrete use case implementations belong in application/usecase/.");

    @ArchTest
    static final ArchRule use_cases_are_spring_services =
            classes()
                    .that().resideInAPackage(ROOT + ".(*)..application.usecase..")
                    .and().haveSimpleNameEndingWith("UseCase")
                    .should().beAnnotatedWith("org.springframework.stereotype.Service")
                    .because("@Service makes use cases injectable via their input port interface.");

    // ── 7. Application layer never imports adapters ───────────────────────────

    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".(*)..adapters..")
                    .because("Application calls output ports (interfaces). " +
                             "Spring wires the adapter implementation at runtime via @Repository/@Component.");

    // ── 8. No JdbcTemplate or JPA in application layer ───────────────────────

    @ArchTest
    static final ArchRule application_has_no_jdbc_template =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..application..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                    .because("JdbcTemplate belongs in adapters/persistence. " +
                             "Use cases call output ports — the adapter owns the SQL.");

    @ArchTest
    static final ArchRule application_has_no_jpa =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..")
                    .because("JPA belongs in adapters/persistence adapters.");

    // ── 9. No concrete infra clients in application layer ─────────────────────

    @ArchTest
    static final ArchRule application_does_not_use_daraja_client =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..application..")
                    .should().dependOnClassesThat().haveSimpleName("MpesaDarajaClient")
                    .because("Use cases depend on MpesaGateway (output port), not MpesaDarajaClient.");

    @ArchTest
    static final ArchRule application_does_not_use_africastalking_gateway =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..application..")
                    .should().dependOnClassesThat().haveSimpleName("AfricasTalkingGateway")
                    .because("Use cases depend on SmsGateway (output port), not AfricasTalkingGateway.");

    // ── 10. Web adapters inject input ports, not concrete use cases ───────────

    @ArchTest
    static final ArchRule web_adapters_do_not_call_concrete_use_cases =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..adapters.web..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".(*)..application.usecase..")
                    .because("Controllers inject input port interfaces (application/port/in/). " +
                             "Never concrete use case classes — that bypasses the port boundary.");

    @ArchTest
    static final ArchRule scheduler_adapters_do_not_call_concrete_use_cases =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..adapters.scheduler..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".(*)..application.usecase..")
                    .because("Schedulers inject input port interfaces, not concrete use case classes.");

    // ── 11. @RestController only in adapters/web ─────────────────────────────

    @ArchTest
    static final ArchRule rest_controllers_only_in_adapters_web =
            classes()
                    .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().resideInAPackage(ROOT + ".(*)..adapters.web..")
                    .because("Controllers are primary adapters — they belong in adapters/web/.");

    // ── 12. @Scheduled only in adapters/scheduler ────────────────────────────

    @ArchTest
    static final ArchRule scheduled_methods_only_in_adapters_scheduler =
            noMethods()
                    .that().areAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                    .should().beDeclaredInClassesThat()
                    .resideOutsideOfPackage(ROOT + ".(*)..adapters.scheduler..")
                    .because("Scheduled jobs are primary adapters — @Scheduled belongs in adapters/scheduler/.");

    // ── 13. @Repository only in adapters/persistence ─────────────────────────

    @ArchTest
    static final ArchRule repositories_only_in_adapters_persistence =
            classes()
                    .that().areAnnotatedWith("org.springframework.stereotype.Repository")
                    .should().resideInAPackage(ROOT + ".(*)..adapters.persistence..")
                    .because("@Repository marks secondary adapters implementing output ports.");

    // ── 14. @Entity only in adapters/persistence ─────────────────────────────

    @ArchTest
    static final ArchRule jpa_entities_only_in_adapters_persistence =
            classes()
                    .that().areAnnotatedWith("jakarta.persistence.Entity")
                    .should().resideInAPackage(ROOT + ".(*)..adapters.persistence..")
                    .because("JPA entity classes are adapter concerns, separate from domain models.");

    // ── 15. Domain models are plain Java — not Spring beans ───────────────────

    @ArchTest
    static final ArchRule domain_models_are_not_spring_beans =
            noClasses()
                    .that().resideInAPackage(ROOT + ".(*)..domain.model..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .because("Domain models are pure Java value objects. The container must not manage them.");

    // ── 16. No cycles between bounded contexts ────────────────────────────────

    @ArchTest
    static final ArchRule no_cycles_between_bounded_contexts =
            slices()
                    .matching(ROOT + ".(*).(*)..").namingSlices("$1")
                    .should().beFreeOfCycles()
                    .because("Bounded contexts must not have circular dependencies.");

    // ── 17. Output port implementations in the right adapter sub-package ──────

    @ArchTest
    static final ArchRule mpesa_gateway_implemented_only_in_mpesa_adapter =
            classes()
                    .that().implement(ROOT + ".payment.application.port.out.MpesaGateway")
                    .should().resideInAPackage(ROOT + ".payment.adapters.mpesa..")
                    .because("MpesaGateway implementation is an adapter — it lives in adapters/mpesa/.");

    @ArchTest
    static final ArchRule sms_gateway_implemented_only_in_sms_adapter =
            classes()
                    .that().implement(ROOT + ".notification.application.port.out.SmsGateway")
                    .should().resideInAPackage(ROOT + ".notification.adapters.sms..")
                    .because("SmsGateway implementation is an adapter — it lives in adapters/sms/.");

    // ── 18. Adapters must not reach across bounded context adapter boundaries ──

    @ArchTest
    static final ArchRule adapters_do_not_cross_context_adapter_boundaries =
            noClasses()
                    .that().resideInAPackage(ROOT + ".loan.adapters.web..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT + ".contribution.adapters..",
                            ROOT + ".group.adapters..",
                            ROOT + ".payment.adapters..",
                            ROOT + ".identity.adapters..")
                    .because("Cross-context calls must go through input/output ports, not adapter-to-adapter.");
}
