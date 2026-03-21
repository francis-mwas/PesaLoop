package com.pesaloop.identity;

import com.pesaloop.identity.domain.service.FieldValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FieldValidationService")
class FieldValidationServiceTest {

    private FieldValidationService service;

    @BeforeEach
    void setUp() { service = new FieldValidationService(); }

    @Nested
    @DisplayName("Phone normalization and validation")
    class Phone {

        @Test @DisplayName("254712345678 → valid, unchanged")
        void standard254() {
            var r = service.validatePhone("254712345678");
            assertThat(r.valid()).isTrue();
            assertThat(r.normalizedValue()).isEqualTo("254712345678");
        }

        @Test @DisplayName("0712345678 → normalized to 254712345678")
        void zeroPrefix() {
            String normalized = service.normalizePhone("0712345678");
            assertThat(normalized).isEqualTo("254712345678");
            assertThat(service.validatePhone(normalized).valid()).isTrue();
        }

        @Test @DisplayName("+254712345678 → normalized to 254712345678")
        void plusPrefix() {
            String normalized = service.normalizePhone("+254712345678");
            assertThat(normalized).isEqualTo("254712345678");
            assertThat(service.validatePhone(normalized).valid()).isTrue();
        }

        @Test @DisplayName("712345678 (9 digits starting with 7) → normalized to 254712345678")
        void nineDigits() {
            String normalized = service.normalizePhone("712345678");
            assertThat(normalized).isEqualTo("254712345678");
            assertThat(service.validatePhone(normalized).valid()).isTrue();
        }

        @Test @DisplayName("0722 000 000 (spaces) → normalized and valid")
        void withSpaces() {
            String normalized = service.normalizePhone("0722 000 000");
            assertThat(service.validatePhone(normalized).valid()).isTrue();
        }

        @Test @DisplayName("Invalid: 11 digits")
        void tooLong() {
            assertThat(service.validatePhone("25471234567890").valid()).isFalse();
        }

        @Test @DisplayName("Invalid: contains letters")
        void withLetters() {
            assertThat(service.validatePhone("254ABC345678").valid()).isFalse();
        }

        @Test @DisplayName("Invalid: null")
        void nullPhone() {
            assertThat(service.validatePhone(null).valid()).isFalse();
        }

        @Test @DisplayName("Invalid: empty")
        void emptyPhone() {
            assertThat(service.validatePhone("").valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("National ID validation")
    class NationalId {

        @Test @DisplayName("8-digit ID is valid")
        void eightDigits() {
            assertThat(service.validateNationalId("12345678").valid()).isTrue();
        }

        @Test @DisplayName("7-digit ID (older format) is valid")
        void sevenDigits() {
            assertThat(service.validateNationalId("1234567").valid()).isTrue();
        }

        @Test @DisplayName("null is valid (optional field)")
        void nullIsValid() {
            assertThat(service.validateNationalId(null).valid()).isTrue();
        }

        @Test @DisplayName("blank is valid (optional field)")
        void blankIsValid() {
            assertThat(service.validateNationalId("").valid()).isTrue();
        }

        @Test @DisplayName("6 digits is invalid")
        void tooShort() {
            assertThat(service.validateNationalId("123456").valid()).isFalse();
        }

        @Test @DisplayName("9 digits is invalid")
        void tooLong() {
            assertThat(service.validateNationalId("123456789").valid()).isFalse();
        }

        @Test @DisplayName("Contains letters is invalid")
        void withLetters() {
            assertThat(service.validateNationalId("1234567A").valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Email validation")
    class Email {

        @Test @DisplayName("Valid email")
        void validEmail() {
            var r = service.validateEmail("alice@example.com");
            assertThat(r.valid()).isTrue();
            assertThat(r.normalizedValue()).isEqualTo("alice@example.com");
        }

        @Test @DisplayName("null is valid (optional)")
        void nullValid() {
            assertThat(service.validateEmail(null).valid()).isTrue();
        }

        @Test @DisplayName("Empty is valid (optional)")
        void emptyValid() {
            assertThat(service.validateEmail("").valid()).isTrue();
        }

        @Test @DisplayName("Missing @ is invalid")
        void missingAt() {
            assertThat(service.validateEmail("aliceexample.com").valid()).isFalse();
        }

        @Test @DisplayName("Missing TLD is invalid")
        void missingTld() {
            assertThat(service.validateEmail("alice@example").valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Date of birth validation")
    class DateOfBirth {

        @Test @DisplayName("25 years old is valid")
        void validAge() {
            assertThat(service.validateDateOfBirth(LocalDate.now().minusYears(25)).valid()).isTrue();
        }

        @Test @DisplayName("Exactly 18 is valid")
        void exactly18() {
            assertThat(service.validateDateOfBirth(LocalDate.now().minusYears(18)).valid()).isTrue();
        }

        @Test @DisplayName("17 years old is rejected")
        void under18() {
            var r = service.validateDateOfBirth(LocalDate.now().minusYears(17));
            assertThat(r.valid()).isFalse();
            assertThat(r.error()).contains("18");
        }

        @Test @DisplayName("Future date is rejected")
        void futureDate() {
            assertThat(service.validateDateOfBirth(LocalDate.now().plusDays(1)).valid()).isFalse();
        }

        @Test @DisplayName("null is valid (optional)")
        void nullValid() {
            assertThat(service.validateDateOfBirth(null).valid()).isTrue();
        }
    }
}
