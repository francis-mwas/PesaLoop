package com.pesaloop.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Bounded context isolation tests.
 *
 * These verify that cross-context dependencies only flow through
 * the correct seams — domain ports and shared kernel — never through
 * direct adapter-to-adapter or use-case-to-use-case calls.
 *
 * Allowed cross-context dependencies:
 *   ✓ Any context → shared (Money, TenantContext, ApiResponse)
 *   ✓ contribution → group.domain.port  (to look up members/groups)
 *   ✓ loan → group.domain.port          (to look up members/groups)
 *   ✓ loan → payment.domain.port        (for payment recording)
 *   ✓ payment → group.domain.port       (for group lookups)
 *   ✓ notification → payment.domain.port (for notification logging)
 *
 * Forbidden cross-context dependencies:
 *   ✗ Any context → another context's adapters
 *   ✗ Any context → another context's application layer
 *   ✗ Adapter-to-adapter across contexts
 */
@AnalyzeClasses(
        packages = "com.pesaloop",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class
)
public class BoundedContextIsolationTest {

    private static final String ROOT = "com.pesaloop";

    // ── No context may import another context's adapters ─────────────────────

    @ArchTest
    static final ArchRule loan_does_not_import_contribution_adapters =
            noClasses()
                    .that().resideInAPackage(ROOT + ".loan..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".contribution.adapters..")
                    .because("The loan context must not depend on contribution adapters. " +
                             "If it needs contribution data, access it through contribution domain ports.");

    @ArchTest
    static final ArchRule contribution_does_not_import_loan_adapters =
            noClasses()
                    .that().resideInAPackage(ROOT + ".contribution..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".loan.adapters..")
                    .because("The contribution context must not depend on loan adapters.");

    @ArchTest
    static final ArchRule payment_does_not_import_loan_adapters =
            noClasses()
                    .that().resideInAPackage(ROOT + ".payment..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".loan.adapters..")
                    .because("The payment context must not depend on loan adapters directly.");

    @ArchTest
    static final ArchRule identity_does_not_import_business_adapters =
            noClasses()
                    .that().resideInAPackage(ROOT + ".identity..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT + ".loan.adapters..",
                            ROOT + ".contribution.adapters..",
                            ROOT + ".payment.adapters..")
                    .because("Identity is independent of business contexts. " +
                             "Authentication must never be coupled to business adapter implementations.");

    // ── No context may import another context's application layer ─────────────

    @ArchTest
    static final ArchRule loan_does_not_import_contribution_application =
            noClasses()
                    .that().resideInAPackage(ROOT + ".loan.application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".contribution.application..")
                    .because("Bounded contexts must not call each other's use cases directly. " +
                             "This would create tight coupling between contexts. Use domain events " +
                             "or a shared port interface instead.");

    @ArchTest
    static final ArchRule contribution_does_not_import_loan_application =
            noClasses()
                    .that().resideInAPackage(ROOT + ".contribution.application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".loan.application..")
                    .because("Bounded contexts must not call each other's use cases directly.");

    @ArchTest
    static final ArchRule identity_does_not_import_business_application =
            noClasses()
                    .that().resideInAPackage(ROOT + ".identity.application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT + ".loan.application..",
                            ROOT + ".contribution.application..",
                            ROOT + ".payment.application..")
                    .because("Identity (auth) must not depend on business context use cases.");

    // ── Adapter-to-adapter cross-context is forbidden ────────────────────────

    @ArchTest
    static final ArchRule web_adapters_do_not_cross_context_boundaries =
            noClasses()
                    .that().resideInAPackage(ROOT + ".loan.adapters.web..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT + ".contribution.adapters..",
                            ROOT + ".group.adapters..",
                            ROOT + ".payment.adapters..",
                            ROOT + ".identity.adapters..")
                    .because("Loan web adapters (controllers) must not import adapters from other " +
                             "bounded contexts. Cross-context calls go through use cases and ports.");

    @ArchTest
    static final ArchRule persistence_adapters_do_not_cross_context_boundaries =
            noClasses()
                    .that().resideInAPackage(ROOT + ".loan.adapters.persistence..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            ROOT + ".contribution.adapters..",
                            ROOT + ".group.adapters..",
                            ROOT + ".identity.adapters..")
                    .because("Loan persistence adapters must not import other contexts' adapters.");

    // ── Domain models are not shared directly between contexts ────────────────

    @ArchTest
    static final ArchRule contribution_does_not_use_loan_domain_models =
            noClasses()
                    .that().resideInAPackage(ROOT + ".contribution..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".loan.domain.model..")
                    .because("Contribution context must not directly use loan domain models. " +
                             "If shared concepts are needed, extract them to the shared kernel.");

    @ArchTest
    static final ArchRule payment_does_not_use_loan_domain_models =
            noClasses()
                    .that().resideInAPackage(ROOT + ".payment..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".loan.domain.model..")
                    .because("Payment context must not depend on loan domain models directly.");

    // ── Shared kernel is truly shared (all may use it) ────────────────────────

    @ArchTest
    static final ArchRule all_contexts_may_use_shared_domain =
            classes()
                    .that().resideInAPackage(ROOT + ".shared.domain..")
                    .should().bePublic()
                    .because("Shared domain classes (Money, TenantContext) must be public " +
                             "so all bounded contexts can use them.");

    // ── Payout context is self-contained ──────────────────────────────────────

    @ArchTest
    static final ArchRule payout_does_not_import_loan_adapters =
            noClasses()
                    .that().resideInAPackage(ROOT + ".payout..")
                    .should().dependOnClassesThat()
                    .resideInAPackage(ROOT + ".loan.adapters..")
                    .because("Payout context is independent of loan adapter implementations.");
}
