package com.pesaloop.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable value object representing a monetary amount.
 * All arithmetic uses HALF_EVEN rounding to avoid accumulated rounding errors
 * (the bug we saw in the chamaa spreadsheet: KES -495 deficit).
 * Stored internally as BigDecimal with 2 decimal places.
 */
public final class Money {

    public static final Money ZERO_KES = Money.of(BigDecimal.ZERO, "KES");

    private final BigDecimal amount;
    private final String currencyCode;

    private Money(BigDecimal amount, String currencyCode) {
        // Validate currency
        Currency.getInstance(currencyCode); // throws if invalid
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.currencyCode = currencyCode;
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currencyCode, "Currency cannot be null");
        Currency.getInstance(currencyCode); // throws IllegalArgumentException for invalid codes
        return new Money(amount, currencyCode);
    }

    public static Money ofKes(BigDecimal amount) {
        return of(amount, "KES");
    }

    public static Money ofKes(long amount) {
        return of(BigDecimal.valueOf(amount), "KES");
    }

    public static Money ofKes(String amount) {
        return of(new BigDecimal(amount), "KES");
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currencyCode);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currencyCode);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currencyCode);
    }

    public Money multiply(long factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    public Money divide(BigDecimal divisor) {
        return new Money(this.amount.divide(divisor, 2, RoundingMode.HALF_EVEN), this.currencyCode);
    }

    public Money percentage(BigDecimal percent) {
        // e.g. percentage(BigDecimal.valueOf(10)) = 10% of this amount
        return multiply(percent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN));
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: %s vs %s".formatted(this.currencyCode, other.currencyCode));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0 && currencyCode.equals(money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currencyCode);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(currencyCode, amount.toPlainString());
    }
}