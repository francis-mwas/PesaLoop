package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.PaymentAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Secondary adapter — group payment accounts and subscription data. */
@Repository
@RequiredArgsConstructor
public class PaymentAccountJdbcAdapter implements PaymentAccountRepository {

    private final JdbcTemplate jdbc;

    @Value("${pesaloop.billing.paybill:123456}")
    private String pesaLoopPaybill;

    @Override
    public List<PaymentAccountRow> findByGroupId(UUID groupId) {
        return jdbc.query(
                """
                SELECT id, account_type, provider, account_number, account_name,
                       bank_branch, bank_swift_code, c2b_registered, c2b_registered_at,
                       is_collection, is_disbursement, is_primary, status, display_label, created_at
                  FROM group_payment_accounts
                 WHERE group_id=? AND status!='INACTIVE'
                 ORDER BY is_primary DESC, is_collection DESC, created_at
                """,
                (rs, row) -> new PaymentAccountRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("account_type"), rs.getString("provider"),
                        rs.getString("account_number"), rs.getString("account_name"),
                        rs.getString("bank_branch"), rs.getString("bank_swift_code"),
                        rs.getBoolean("c2b_registered"),
                        rs.getObject("c2b_registered_at", Instant.class),
                        rs.getBoolean("is_collection"), rs.getBoolean("is_disbursement"),
                        rs.getBoolean("is_primary"), rs.getString("status"),
                        rs.getString("display_label"),
                        rs.getObject("created_at", Instant.class)),
                groupId);
    }

    @Override
    public UUID createAccount(UUID groupId, UUID createdByUserId, String accountType,
                               String provider, String accountNumber, String accountName,
                               String bankBranch, String bankSwiftCode, String displayLabel,
                               boolean isCollection, boolean isDisbursement) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO group_payment_accounts
                    (id, group_id, account_type, provider, account_number, account_name,
                     bank_branch, bank_swift_code, display_label,
                     is_collection, is_disbursement, is_primary, status,
                     created_by, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,FALSE,'ACTIVE',?,NOW(),NOW())
                """,
                id, groupId, accountType, provider, accountNumber, accountName,
                bankBranch, bankSwiftCode, displayLabel,
                isCollection, isDisbursement, createdByUserId);
        return id;
    }

    @Override
    public void deactivateAccount(UUID accountId, UUID groupId) {
        jdbc.update("UPDATE group_payment_accounts SET status='INACTIVE',updated_at=NOW() WHERE id=? AND group_id=?",
                accountId, groupId);
    }

    @Override
    public void setPrimary(UUID accountId, UUID groupId, boolean isCollection, boolean isDisbursement) {
        if (isCollection) jdbc.update(
                "UPDATE group_payment_accounts SET is_primary=FALSE WHERE group_id=? AND is_collection=TRUE AND is_primary=TRUE",
                groupId);
        if (isDisbursement) jdbc.update(
                "UPDATE group_payment_accounts SET is_primary=FALSE WHERE group_id=? AND is_disbursement=TRUE AND is_primary=TRUE",
                groupId);
        jdbc.update("UPDATE group_payment_accounts SET is_primary=TRUE,updated_at=NOW() WHERE id=? AND group_id=?",
                accountId, groupId);
    }

    @Override
    public Optional<String> findShortcodeById(UUID accountId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT account_number FROM group_payment_accounts WHERE id=?",
                    String.class, accountId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public void markC2bRegistered(UUID accountId) {
        jdbc.update("UPDATE group_payment_accounts SET c2b_registered=TRUE,c2b_registered_at=NOW() WHERE id=?",
                accountId);
    }

    @Override
    public SubscriptionRow findSubscription(UUID groupId) {
        return jdbc.queryForObject(
                """
                SELECT gs.status, gs.plan_code, sp.name AS plan_name,
                       sp.monthly_fee_kes, gs.current_period_end, gs.trial_ends_at, g.slug
                  FROM group_subscriptions gs
                  JOIN subscription_plans sp ON sp.code = gs.plan_code
                  JOIN groups g ON g.id = gs.group_id
                 WHERE gs.group_id = ?
                """,
                (rs, row) -> new SubscriptionRow(
                        rs.getString("status"), rs.getString("plan_code"),
                        rs.getString("plan_name"), rs.getBigDecimal("monthly_fee_kes"),
                        rs.getObject("current_period_end", Instant.class),
                        rs.getObject("trial_ends_at", Instant.class),
                        pesaLoopPaybill, rs.getString("slug")),
                groupId);
    }
}
