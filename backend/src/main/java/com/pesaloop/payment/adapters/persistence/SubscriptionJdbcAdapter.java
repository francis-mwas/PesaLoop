package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary adapter — implements SubscriptionRepository using JDBC.
 * All subscription SQL lives here. SubscriptionLifecycleService
 * never sees JdbcTemplate.
 */
@Repository
@RequiredArgsConstructor
public class SubscriptionJdbcAdapter implements SubscriptionRepository {

    private final JdbcTemplate jdbc;

    @Override
    public List<GroupLifecycleRow> findGroupsNeedingAction() {
        return jdbc.query(
                """
                SELECT group_id, group_name, group_slug, plan_code, status,
                       days_remaining, required_action, admin_name, admin_phone,
                       reminder_count
                  FROM v_subscription_lifecycle
                 WHERE required_action != 'NO_ACTION'
                """,
                (rs, row) -> new GroupLifecycleRow(
                        UUID.fromString(rs.getString("group_id")),
                        rs.getString("group_name"),
                        rs.getString("group_slug"),
                        rs.getString("plan_code"),
                        rs.getString("status"),
                        rs.getObject("days_remaining") != null
                                ? rs.getInt("days_remaining") : null,
                        rs.getString("required_action"),
                        rs.getString("admin_name"),
                        rs.getString("admin_phone"),
                        rs.getInt("reminder_count")
                )
        );
    }

    @Override
    public String findStatus(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT status FROM group_subscriptions WHERE group_id=?",
                String.class, groupId);
    }

    @Override
    public void startGrace(UUID groupId, int graceDays) {
        jdbc.update(
                """
                UPDATE group_subscriptions
                   SET status           = 'GRACE',
                       grace_period_end = NOW() + (? || ' days')::INTERVAL,
                       updated_at       = NOW()
                 WHERE group_id = ?
                """,
                graceDays, groupId);
    }

    @Override
    public void suspend(UUID groupId) {
        jdbc.update(
                "UPDATE group_subscriptions SET status='SUSPENDED', suspended_at=NOW(), updated_at=NOW() WHERE group_id=?",
                groupId);
    }

    @Override
    public void markDormant(UUID groupId) {
        jdbc.update(
                "UPDATE group_subscriptions SET status='DORMANT', dormant_at=NOW(), updated_at=NOW() WHERE group_id=?",
                groupId);
    }

    @Override
    public void activate(UUID groupId, String planCode,
                         LocalDate periodStart, LocalDate periodEnd,
                         Instant nextInvoiceAt, String mpesaRef) {
        jdbc.update(
                """
                UPDATE group_subscriptions
                   SET status               = 'ACTIVE',
                       plan_code            = ?,
                       current_period_start = ?,
                       current_period_end   = ?,
                       paid_through_date    = ?,
                       next_invoice_at      = ?,
                       suspended_at         = NULL,
                       grace_period_end     = NULL,
                       last_reminder_sent_at = NULL,
                       reminder_count       = 0,
                       reactivated_count    = reactivated_count +
                           CASE WHEN status IN ('SUSPENDED','DORMANT','GRACE') THEN 1 ELSE 0 END,
                       updated_at           = NOW()
                 WHERE group_id = ?
                """,
                planCode, periodStart, periodEnd, periodEnd,
                nextInvoiceAt, groupId);

        if (mpesaRef != null) {
            jdbc.update(
                    """
                    UPDATE subscription_invoices
                       SET status='PAID', paid_at=NOW(), mpesa_ref=?
                     WHERE group_id=? AND status IN ('PENDING','OVERDUE')
                     ORDER BY created_at DESC
                     LIMIT 1
                    """,
                    mpesaRef, groupId);
        }
    }

    @Override
    public void cancel(UUID groupId, String reason, UUID cancelledByUserId) {
        int exportDays = getConfig("post_cancel_export_window_days", 30);
        jdbc.update(
                """
                UPDATE group_subscriptions
                   SET status               = 'CANCELLED',
                       cancelled_at         = NOW(),
                       cancellation_reason  = ?,
                       current_period_end   = CURRENT_DATE + (? || ' days')::INTERVAL,
                       updated_at           = NOW()
                 WHERE group_id = ?
                """,
                reason, exportDays, groupId);
    }

    @Override
    public void recordReminderSent(UUID groupId) {
        jdbc.update(
                "UPDATE group_subscriptions SET last_reminder_sent_at=NOW(), reminder_count=reminder_count+1, updated_at=NOW() WHERE group_id=?",
                groupId);
    }

    @Override
    public void generateInvoice(UUID groupId, String planCode, BigDecimal feeKes, String groupSlug) {
        jdbc.update(
                """
                INSERT INTO subscription_invoices
                    (id, group_id, plan_code, period_start, period_end,
                     base_fee_kes, sms_charges_kes, b2c_fee_kes, total_kes,
                     status, payment_reference, created_at)
                VALUES (gen_random_uuid(), ?, ?,
                        CURRENT_DATE, CURRENT_DATE+INTERVAL '1 month',
                        ?, 0, 0, ?, 'PENDING', ?, NOW())
                """,
                groupId, planCode, feeKes, feeKes, groupSlug);
    }

    @Override
    public void markInvoicePaid(UUID groupId, String mpesaRef) {
        jdbc.update(
                "UPDATE subscription_invoices SET status='PAID',paid_at=NOW(),mpesa_ref=? WHERE group_id=? AND status IN ('PENDING','OVERDUE') ORDER BY created_at DESC LIMIT 1",
                mpesaRef, groupId);
    }

    @Override
    public BigDecimal monthlyFeeForPlan(String planCode) {
        try {
            return jdbc.queryForObject(
                    "SELECT monthly_fee_kes FROM subscription_plans WHERE code=?",
                    BigDecimal.class, planCode);
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    @Override
    public String findGroupSlug(UUID groupId) {
        return jdbc.queryForObject("SELECT slug FROM groups WHERE id=?", String.class, groupId);
    }

    @Override
    public int getConfig(String key, int defaultValue) {
        try {
            Integer val = jdbc.queryForObject(
                    "SELECT config_value::INT FROM platform_config WHERE config_key=? AND group_id IS NULL",
                    Integer.class, key);
            return val != null ? val : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }

    @Override
    public void logEvent(UUID groupId, String eventType, String fromStatus, String toStatus,
                         String triggeredBy, UUID actorId, String notes) {
        jdbc.update(
                "INSERT INTO subscription_events (id,group_id,event_type,from_status,to_status,triggered_by,actor_id,notes,created_at) VALUES (gen_random_uuid(),?,?,?,?,?,?,?,NOW())",
                groupId, eventType, fromStatus, toStatus, triggeredBy, actorId, notes);
    }

    @Override
    public Optional<UUID> findGroupIdBySlug(String slug) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id FROM groups WHERE slug=? AND status='ACTIVE'", UUID.class, slug));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<UUID> findGroupIdByName(String namePart) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id FROM groups WHERE LOWER(name) LIKE LOWER(?) AND status='ACTIVE' LIMIT 1",
                    UUID.class, "%" + namePart + "%"));
        } catch (Exception e) { return Optional.empty(); }
    }

}
