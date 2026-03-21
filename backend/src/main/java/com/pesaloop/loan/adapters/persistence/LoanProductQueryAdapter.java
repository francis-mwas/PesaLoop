package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.application.port.out.LoanProductQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Secondary adapter — loan product update/deactivation and active loan counts. */
@Repository
@RequiredArgsConstructor
public class LoanProductQueryAdapter implements LoanProductQueryRepository {

    private final JdbcTemplate jdbc;

    @Override
    public int countActiveLoansUnderProduct(UUID productId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM loan_accounts WHERE product_id=? AND status='ACTIVE'",
                Integer.class, productId);
        return count != null ? count : 0;
    }

    @Override
    public void updateProduct(UUID productId, UUID groupId,
                               String name, String description,
                               String interestType, String accrualFrequency,
                               BigDecimal interestRate,
                               BigDecimal minAmount, BigDecimal maxAmount,
                               BigDecimal maxMultipleOfSavings,
                               Integer maxRepaymentPeriods, String repaymentFrequency,
                               Boolean requiresGuarantor, Boolean requiresZeroArrears,
                               BigDecimal lateRepaymentPenaltyRate, Boolean active) {
        jdbc.update(
                """
                UPDATE loan_products
                   SET name                        = COALESCE(?, name),
                       description                 = COALESCE(?, description),
                       interest_type               = COALESCE(?, interest_type),
                       accrual_frequency           = COALESCE(?, accrual_frequency),
                       interest_rate               = COALESCE(?, interest_rate),
                       minimum_amount              = COALESCE(?, minimum_amount),
                       maximum_amount              = COALESCE(?, maximum_amount),
                       max_multiple_of_savings     = COALESCE(?, max_multiple_of_savings),
                       max_repayment_periods       = COALESCE(?, max_repayment_periods),
                       repayment_frequency         = COALESCE(?, repayment_frequency),
                       requires_guarantor          = COALESCE(?, requires_guarantor),
                       requires_zero_arrears       = COALESCE(?, requires_zero_arrears),
                       late_repayment_penalty_rate = COALESCE(?, late_repayment_penalty_rate),
                       active                      = COALESCE(?, active),
                       updated_at                  = NOW()
                 WHERE id = ? AND group_id = ?
                """,
                name, description, interestType, accrualFrequency, interestRate,
                minAmount, maxAmount, maxMultipleOfSavings, maxRepaymentPeriods,
                repaymentFrequency, requiresGuarantor, requiresZeroArrears,
                lateRepaymentPenaltyRate, active, productId, groupId);
    }

    @Override
    public void deactivateProduct(UUID productId, UUID groupId) {
        jdbc.update("UPDATE loan_products SET active=FALSE,updated_at=NOW() WHERE id=? AND group_id=?",
                productId, groupId);
    }
}
