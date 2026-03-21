package com.pesaloop.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Naming convention tests.
 *
 * These ensure anyone reading the code knows exactly what layer a class
 * belongs to from its name and package alone. No guessing.
 *
 * Conventions:
 *   - Domain models: no suffix (LoanAccount, Member, Group)
 *   - Domain ports:  [Name]Repository or [Name]Gateway (interfaces)
 *   - Use cases:     [Action][Noun]UseCase or [Noun]Service (application services)
 *   - JPA entities:  [Name]JpaEntity (adapters/persistence)
 *   - JPA repos:     [Name]JpaRepository (adapters/persistence)
 *   - JDBC adapters: [Name]JdbcAdapter (adapters/persistence)
 *   - Controllers:   [Name]Controller (adapters/web)
 *   - Schedulers:    [Name]Scheduler (adapters/scheduler)
 *   - Port impls:    [Name]Adapter (adapters/*)
 */
@AnalyzeClasses(
        packages = "com.pesaloop",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class
)
public class NamingConventionTest {

    private static final String ROOT = "com.pesaloop";

    // ── JPA entities ──────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule jpa_entities_are_named_with_jpa_entity_suffix =
            classes()
                    .that().areAnnotatedWith("jakarta.persistence.Entity")
                    .should().haveSimpleNameEndingWith("JpaEntity")
                    .because("JPA entities are adapter concerns and must be distinguishable from " +
                             "domain models. LoanAccountJpaEntity vs LoanAccount makes the " +
                             "separation obvious at a glance.");

    // ── JPA Spring Data repositories ──────────────────────────────────────────

    @ArchTest
    static final ArchRule spring_data_repositories_are_named_jpa_repository =
            classes()
                    .that().areInterfaces()
                    .and().resideInAPackage(ROOT + ".(*)..adapters.persistence..")
                    .and().areAssignableTo("org.springframework.data.repository.Repository")
                    .should().haveSimpleNameEndingWith("JpaRepository")
                    .because("Spring Data JPA repositories are adapter plumbing. The JpaRepository " +
                             "suffix distinguishes them from domain port interfaces (which just end in Repository).");

    // ── JDBC adapters ─────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule jdbc_adapters_are_named_jdbc_adapter =
            classes()
                    .that().resideInAPackage(ROOT + ".(*)..adapters.persistence..")
                    .and().areAnnotatedWith("org.springframework.stereotype.Repository")
                    .and().areNotInterfaces()
                    .and().haveSimpleNameNotEndingWith("JpaRepository") // Spring Data repos have @Repository too
                    .should().haveSimpleNameEndingWith("JdbcAdapter")
                    .orShould().haveSimpleNameEndingWith("RepositoryAdapter")
                    .orShould().haveSimpleNameEndingWith("Persistence")  // legacy — being phased out
                    .because("JDBC adapter classes should be named [X]JdbcAdapter to signal " +
                             "they own raw SQL for a specific aggregate.");

    // ── Controllers ───────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule controllers_are_named_controller =
            classes()
                    .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().haveSimpleNameEndingWith("Controller")
                    .because("REST controllers must be named [Name]Controller for discoverability.");

    @ArchTest
    static final ArchRule controllers_are_in_adapters_web =
            classes()
                    .that().haveSimpleNameEndingWith("Controller")
                    .and().areNotInterfaces()
                    .should().resideInAPackage(ROOT + ".(*)..adapters.web..")
                    .because("Controllers are HTTP adapters and belong in adapters/web.");

    // ── Use cases ─────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule use_cases_are_in_application_usecase =
            classes()
                    .that().haveSimpleNameEndingWith("UseCase")
                    .and().areNotInterfaces()
                    .should().resideInAPackage(ROOT + ".(*)..application.usecase..")
                    .because("Use cases are the application core and belong in application/usecase.");

    @ArchTest
    static final ArchRule application_services_are_in_application =
            classes()
                    .that().haveSimpleNameEndingWith("Service")
                    .and().resideInAPackage(ROOT + "..")
                    .and().areAnnotatedWith("org.springframework.stereotype.Service")
                    .and().areNotInterfaces()
                    // Exclude adapter-level services (webhook service etc)
                    .and().resideOutsideOfPackage(ROOT + ".(*)..adapters..")
                    .should().resideInAPackage(ROOT + ".(*)..application..")
                    .because("Application services (use case orchestrators) belong in application layer.");

    // ── Schedulers ────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule schedulers_are_named_scheduler =
            classes()
                    .that().resideInAPackage(ROOT + ".(*)..adapters.scheduler..")
                    .and().areNotInterfaces()
                    .should().haveSimpleNameEndingWith("Scheduler")
                    .because("Scheduler classes drive the application on a timer and must be " +
                             "named [Name]Scheduler.");

    // ── Port adapters ─────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule port_implementations_are_named_adapter_or_persistence =
            classes()
                    .that().resideInAPackage(ROOT + ".(*)..adapters.mpesa..")
                    .and().areNotAnnotatedWith("org.springframework.boot.context.properties.ConfigurationProperties")
                    .and().areNotInterfaces()
                    .should().haveSimpleNameEndingWith("Adapter")
                    .orShould().haveSimpleNameEndingWith("Client")
                    .because("M-Pesa adapter classes should be named [Name]Adapter or [Name]Client.");

    // ── No UseCase suffix outside application ─────────────────────────────────

    @ArchTest
    static final ArchRule use_case_suffix_only_in_application =
            noClasses()
                    .that().haveSimpleNameEndingWith("UseCase")
                    .should().resideOutsideOfPackage(ROOT + ".(*)..application..")
                    .because("The UseCase suffix is reserved for application layer classes. " +
                             "Using it elsewhere misleads developers about which layer a class belongs to.");
}
