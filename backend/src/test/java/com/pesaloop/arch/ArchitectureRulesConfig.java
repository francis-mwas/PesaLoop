package com.pesaloop.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Shared configuration for architecture tests.
 *
 * Provides a pre-imported class set that all architecture test classes
 * can reference for ad-hoc checks, and documents the package conventions
 * used across all tests.
 *
 * Package convention summary:
 *
 *   com.pesaloop.{context}.domain.model      — aggregates, value objects, enums
 *   com.pesaloop.{context}.domain.port       — output port interfaces
 *   com.pesaloop.{context}.domain.service    — pure domain services
 *   com.pesaloop.{context}.application.usecase — use cases (@Service)
 *   com.pesaloop.{context}.application.dto   — DTOs (request/response records)
 *   com.pesaloop.{context}.adapters.persistence — JPA/JDBC adapters (@Repository)
 *   com.pesaloop.{context}.adapters.web      — REST controllers (@RestController)
 *   com.pesaloop.{context}.adapters.scheduler — scheduled adapters (@Scheduled)
 *   com.pesaloop.{context}.adapters.mpesa    — M-Pesa HTTP adapter
 *   com.pesaloop.{context}.adapters.sms      — SMS HTTP adapter
 *   com.pesaloop.{context}.adapters.security — JWT filter and provider
 *
 * Contexts: loan, contribution, group, identity, payment, notification, payout, shared
 */
public class ArchitectureRulesConfig {

    public static final String ROOT_PACKAGE = "com.pesaloop";

    /**
     * Returns all production classes, excluding test classes.
     * Use this for imperative ArchUnit checks in test methods.
     */
    public static JavaClasses allProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE);
    }
}
