package com.pesaloop.payment.domain.service;

import java.math.BigDecimal;

/**
 * Domain service — derives subscription plan from a payment amount.
 *
 * This logic does not belong on any single entity. It encapsulates
 * the business rule: "which plan does a payment of X activate?"
 * Used by PesaLoopBillingUseCase when a C2B payment arrives.
 *
 * Plan tiers (KES):
 *   FREE (trial)  → no payment required
 *   GROWTH        → 500/month
 *   PRO           → 1,500/month
 *   ENTERPRISE    → custom (negotiated offline)
 */
public class SubscriptionPricingService {

    private static final BigDecimal GROWTH_FEE = BigDecimal.valueOf(500);
    private static final BigDecimal PRO_FEE     = BigDecimal.valueOf(1_500);

    /** Returns the plan code that a payment of this amount activates. */
    public String planCodeForPayment(BigDecimal amountKes) {
        if (amountKes == null || amountKes.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (amountKes.compareTo(PRO_FEE) >= 0)     return "PRO";
        if (amountKes.compareTo(GROWTH_FEE) >= 0)  return "GROWTH";
        throw new IllegalArgumentException(
            "Payment of KES " + amountKes + " does not meet the minimum plan fee of KES " + GROWTH_FEE);
    }

    /** Returns the monthly fee in KES for a given plan code. */
    public BigDecimal monthlyFeeFor(String planCode) {
        return switch (planCode) {
            case "GROWTH"     -> GROWTH_FEE;
            case "PRO"        -> PRO_FEE;
            case "FREE"       -> BigDecimal.ZERO;
            case "ENTERPRISE" -> BigDecimal.ZERO; // invoiced separately
            default -> throw new IllegalArgumentException("Unknown plan: " + planCode);
        };
    }

    /** Returns true if the amount exactly covers or exceeds the plan fee. */
    public boolean isValidPlanPayment(String planCode, BigDecimal amountKes) {
        BigDecimal fee = monthlyFeeFor(planCode);
        return fee.compareTo(BigDecimal.ZERO) == 0 || amountKes.compareTo(fee) >= 0;
    }
}
