package com.pesaloop.identity.domain.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralized field validation service.
 * Returns structured validation results — not exceptions — so the caller
 * can collect all errors and return them together (better UX than
 * failing on the first error).
 *
 * Used by both self-registration and admin add-member flows.
 * Pure domain service — no Spring, no JPA.
 */
@Service
public class FieldValidationService {

    // Kenya phone: 254 followed by exactly 9 digits
    // Safaricom: 2547xx, 2541xx
    // Airtel:    2541xx, 2573xx
    // Telkom:    2577xx
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^254[0-9]{9}$");

    // Kenya national ID: 7 or 8 digits (older IDs have 7)
    private static final Pattern NATIONAL_ID_PATTERN =
            Pattern.compile("^[0-9]{7,8}$");

    // RFC 5322 simplified — good enough for Kenya context
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    private static final int MINIMUM_AGE_YEARS = 18;

    // ── Public API ────────────────────────────────────────────────────────────

    public ValidationResult validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return ValidationResult.error("phone", "Phone number is required");
        }
        String cleaned = phone.trim().replaceAll("\\s+", "");
        // Auto-convert 07xx and +254xx formats to 254xx
        cleaned = normalizePhone(cleaned);
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.error("phone",
                    "Invalid phone number. Use format 254XXXXXXXXX (e.g. 254712345678). "
                    + "Do not include spaces or dashes.");
        }
        return ValidationResult.ok("phone", cleaned);
    }

    public ValidationResult validateEmail(String email) {
        if (email == null || email.isBlank()) {
            return ValidationResult.ok("email", null); // email is optional
        }
        String cleaned = email.trim().toLowerCase();
        if (cleaned.length() > 255) {
            return ValidationResult.error("email", "Email must not exceed 255 characters");
        }
        if (!EMAIL_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.error("email", "Invalid email address format");
        }
        return ValidationResult.ok("email", cleaned);
    }

    public ValidationResult validateNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return ValidationResult.ok("nationalId", null); // optional
        }
        String cleaned = nationalId.trim().replaceAll("\\s+", "");
        if (!NATIONAL_ID_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.error("nationalId",
                    "Invalid national ID. Must be 7 or 8 digits (Kenya ID format).");
        }
        return ValidationResult.ok("nationalId", cleaned);
    }

    public ValidationResult validateDateOfBirth(LocalDate dob) {
        if (dob == null) {
            return ValidationResult.ok("dateOfBirth", null); // optional
        }
        if (dob.isAfter(LocalDate.now())) {
            return ValidationResult.error("dateOfBirth", "Date of birth cannot be in the future");
        }
        int age = Period.between(dob, LocalDate.now()).getYears();
        if (age < MINIMUM_AGE_YEARS) {
            return ValidationResult.error("dateOfBirth",
                    "Member must be at least %d years old".formatted(MINIMUM_AGE_YEARS));
        }
        if (age > 120) {
            return ValidationResult.error("dateOfBirth", "Invalid date of birth");
        }
        return ValidationResult.ok("dateOfBirth", dob);
    }

    public ValidationResult validateFullName(String name) {
        if (name == null || name.isBlank()) {
            return ValidationResult.error("fullName", "Full name is required");
        }
        String cleaned = name.trim();
        if (cleaned.length() < 3) {
            return ValidationResult.error("fullName", "Name must be at least 3 characters");
        }
        if (cleaned.length() > 100) {
            return ValidationResult.error("fullName", "Name must not exceed 100 characters");
        }
        return ValidationResult.ok("fullName", cleaned);
    }

    /**
     * Validates all fields at once and returns all errors together.
     */
    public List<ValidationResult> validateAll(
            String phone, String email, String nationalId,
            String fullName, LocalDate dateOfBirth) {

        List<ValidationResult> results = new ArrayList<>();
        results.add(validatePhone(phone));
        results.add(validateEmail(email));
        results.add(validateNationalId(nationalId));
        results.add(validateFullName(fullName));
        if (dateOfBirth != null) results.add(validateDateOfBirth(dateOfBirth));
        return results;
    }

    public boolean hasErrors(List<ValidationResult> results) {
        return results.stream().anyMatch(r -> !r.valid());
    }

    // ── Phone normalization ───────────────────────────────────────────────────

    /**
     * Normalizes various Kenyan phone formats to 254XXXXXXXXX:
     *   07XXXXXXXX   → 2547XXXXXXXX
     *   01XXXXXXXX   → 2541XXXXXXXX
     *   +254XXXXXXXXX → 254XXXXXXXXX
     *   0722...      → 254722...
     */
    public String normalizePhone(String input) {
        if (input == null) return null;
        String p = input.trim().replaceAll("[\\s\\-()]", "");

        if (p.startsWith("+254")) return p.substring(1);            // +254 → 254
        if (p.startsWith("0") && p.length() == 10) return "254" + p.substring(1); // 07xx → 254
        if (p.startsWith("7") && p.length() == 9) return "254" + p;  // 7xx → 254
        if (p.startsWith("1") && p.length() == 9) return "254" + p;  // 1xx → 254
        return p;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    public record ValidationResult(
            String field,
            boolean valid,
            String error,
            Object normalizedValue   // the cleaned/normalized value if valid
    ) {
        static ValidationResult ok(String field, Object normalized) {
            return new ValidationResult(field, true, null, normalized);
        }
        static ValidationResult error(String field, String error) {
            return new ValidationResult(field, false, error, null);
        }
    }
}
