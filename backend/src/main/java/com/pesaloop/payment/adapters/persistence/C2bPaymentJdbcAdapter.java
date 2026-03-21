package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.C2bPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Secondary adapter — C2B paybill payment persistence. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class C2bPaymentJdbcAdapter implements C2bPaymentRepository {

    private final JdbcTemplate jdbc;

    @Override
    public boolean alreadyProcessed(String mpesaTransactionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_records WHERE mpesa_transaction_id=?",
                Integer.class, mpesaTransactionId);
        return count != null && count > 0;
    }

    @Override
    public Optional<UUID> findOpenEntryId(UUID groupId, UUID memberId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT e.id FROM contribution_entries e
                      JOIN contribution_cycles c ON c.id=e.cycle_id
                     WHERE e.group_id=? AND e.member_id=?
                       AND c.status IN ('OPEN','GRACE_PERIOD')
                       AND e.status IN ('PENDING','PARTIAL')
                     ORDER BY c.due_date DESC LIMIT 1
                    """,
                    (rs, r) -> UUID.fromString(rs.getString("id")),
                    groupId, memberId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    @Transactional
    public void applyC2bPayment(UUID groupId, UUID memberId, UUID entryId,
                                  BigDecimal amount, String mpesaTransId,
                                  String phone, String methodName) {
        if (entryId != null) {
            jdbc.update(
                    """
                    UPDATE contribution_entries
                       SET paid_amount=paid_amount+?, last_payment_method=?,
                           last_mpesa_reference=?,
                           first_payment_at=COALESCE(first_payment_at,NOW()),
                           fully_paid_at=CASE WHEN paid_amount+?>=expected_amount THEN NOW() ELSE fully_paid_at END,
                           status=CASE WHEN paid_amount+?>=expected_amount THEN 'PAID' ELSE 'PARTIAL' END,
                           updated_at=NOW()
                     WHERE id=?
                    """,
                    amount, methodName, mpesaTransId, amount, amount, entryId);
            jdbc.update(
                    "UPDATE contribution_cycles SET total_collected_amount=total_collected_amount+? WHERE id=(SELECT cycle_id FROM contribution_entries WHERE id=?)",
                    amount, entryId);
        }
        jdbc.update("UPDATE members SET savings_balance=savings_balance+? WHERE id=?", amount, memberId);
        jdbc.update(
                """
                INSERT INTO payment_records
                    (id,group_id,entry_id,member_id,payment_type,amount,currency_code,
                     payment_method,mpesa_reference,mpesa_transaction_id,phone_number,status,recorded_at)
                VALUES(gen_random_uuid(),?,?,?,'CONTRIBUTION',?,'KES',?,?,?,?,'COMPLETED',NOW())
                """,
                groupId, entryId, memberId, amount, methodName, mpesaTransId, mpesaTransId, phone);
    }

    @Override
    public void saveUnmatched(String transId, BigDecimal amount, String phone,
                               String billRef, String shortCode) {
        jdbc.update(
                """
                INSERT INTO unmatched_payments
                    (id,mpesa_transaction_id,amount,phone_number,bill_ref_number,business_short_code,resolved,received_at)
                VALUES(gen_random_uuid(),?,?,?,?,?,FALSE,NOW())
                ON CONFLICT (mpesa_transaction_id) DO NOTHING
                """,
                transId, amount, phone, billRef, shortCode);
    }
}
