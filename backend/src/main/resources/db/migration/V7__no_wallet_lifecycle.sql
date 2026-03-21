-- ============================================================
-- V7__no_wallet_lifecycle.sql
-- 1. Platform configuration table (all configurable durations)
-- 2. Disbursement instructions (replaces B2C calls)
-- 3. Subscription lifecycle hardening
-- 4. Remove wallet_balance from group_subscriptions
-- 5. LoanStatus: add PENDING_DISBURSEMENT
-- ============================================================

-- ── 1. Platform configuration ─────────────────────────────────────────────────
-- All durations and thresholds configurable by super admin.
-- Per-group overrides stored with group_id; platform defaults have group_id = NULL.

CREATE TABLE platform_config (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID        REFERENCES groups(id),
    -- NULL = platform-wide default; non-null = per-group override

    config_key      VARCHAR(80) NOT NULL,
    config_value    TEXT        NOT NULL,
    value_type      VARCHAR(10) NOT NULL DEFAULT 'INTEGER'
                        CHECK (value_type IN ('INTEGER','TEXT','BOOLEAN','JSON')),
    description     TEXT,

    set_by          UUID        REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (group_id, config_key)
);

CREATE INDEX idx_platform_config_key ON platform_config(config_key)
    WHERE group_id IS NULL;

-- Platform-wide defaults
INSERT INTO platform_config (config_key, config_value, value_type, description) VALUES
('trial_days',                      '30',   'INTEGER', 'How many days a new group gets as free trial'),
('grace_days',                      '7',    'INTEGER', 'Grace period after trial/invoice expiry before suspension'),
('dormant_after_suspended_days',    '90',   'INTEGER', 'Days suspended before moving to DORMANT (SMS stops)'),
('data_retention_after_cancel_months', '12','INTEGER', 'Months data is kept after explicit cancellation'),
('post_cancel_export_window_days',  '30',   'INTEGER', 'Days after cancellation where read-only access continues'),
('disbursement_instruction_expiry_hours', '72', 'INTEGER', 'Hours before an unconfirmed disbursement instruction expires'),
('max_pending_disbursements_per_group',   '10', 'INTEGER', 'Max unconfirmed disbursement instructions per group'),
('subscription_billing_day',        '1',    'INTEGER', 'Day of month invoices are generated (1 = 1st of month)'),
('invoice_due_days',                '7',    'INTEGER', 'Days after invoice generation before it is overdue'),
('reminder_sms_days_before_grace_end', '3,1', 'TEXT',  'Days before grace ends to send reminder SMS (comma separated)'),
('sms_sender_id',                   'PesaLoop', 'TEXT','Default SMS sender ID');

-- ── 2. Disbursement instructions ──────────────────────────────────────────────
-- Every loan disbursement, MGR payout, and dividend payment generates
-- an instruction that the treasurer must manually execute and confirm.
-- PesaLoop never initiates a money transfer — it only records that one happened.

CREATE TABLE disbursement_instructions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id            UUID        NOT NULL REFERENCES groups(id),

    -- What this disbursement is for
    instruction_type    VARCHAR(30) NOT NULL
                            CHECK (instruction_type IN (
                                'LOAN_DISBURSEMENT',
                                'MGR_PAYOUT',
                                'DIVIDEND_PAYMENT',
                                'WELFARE_PAYMENT',
                                'OTHER'
                            )),

    -- Link to the source record
    loan_id             UUID        REFERENCES loan_accounts(id),
    -- For MGR payouts, cycle ID stored here as a text reference
    source_reference    VARCHAR(100),
    -- e.g. "MGR Cycle 4 - 2024" or "Year-end dividend 2024"

    -- Recipient
    recipient_member_id UUID        NOT NULL REFERENCES members(id),
    recipient_name      VARCHAR(100) NOT NULL,
    recipient_phone     VARCHAR(15)  NOT NULL,
    -- Exactly what the treasurer should type into M-Pesa
    suggested_account_reference VARCHAR(50),
    -- e.g. member number or loan reference — for treasurer's own records

    -- Amount
    amount_kes          NUMERIC(15,2) NOT NULL,
    currency_code       VARCHAR(3)    NOT NULL DEFAULT 'KES',

    -- Status machine
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN (
                                'PENDING',      -- waiting for treasurer to disburse
                                'CONFIRMED',    -- treasurer confirmed; M-Pesa ref entered
                                'CANCELLED',    -- cancelled before disbursement (e.g. loan rejected)
                                'EXPIRED'       -- treasurer never confirmed within expiry window
                            )),

    -- Confirmation details (filled by treasurer after disbursing)
    confirmed_by        UUID        REFERENCES users(id),
    confirmed_at        TIMESTAMPTZ,
    external_mpesa_ref  VARCHAR(30),
    -- The M-Pesa confirmation code from the treasurer's own phone (e.g. "SHK7N2A1B3")
    confirmation_notes  TEXT,
    -- Any notes the treasurer adds (e.g. "Sent via Equity Bank instead — ref KCB123")

    -- Expiry
    expires_at          TIMESTAMPTZ NOT NULL,
    -- Set from platform_config disbursement_instruction_expiry_hours

    -- Who issued the instruction (usually auto-generated on loan approval)
    issued_by           UUID        REFERENCES users(id),
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_disbursement_group_status
    ON disbursement_instructions(group_id, status, issued_at DESC);
CREATE INDEX idx_disbursement_loan
    ON disbursement_instructions(loan_id) WHERE loan_id IS NOT NULL;
CREATE INDEX idx_disbursement_member
    ON disbursement_instructions(recipient_member_id);
CREATE INDEX idx_disbursement_mpesa_ref
    ON disbursement_instructions(external_mpesa_ref)
    WHERE external_mpesa_ref IS NOT NULL;

CREATE TRIGGER trg_disbursement_updated
    BEFORE UPDATE ON disbursement_instructions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── 3. Subscription lifecycle hardening ──────────────────────────────────────

-- Remove wallet (PesaLoop does not hold group money)
ALTER TABLE group_subscriptions
    DROP COLUMN IF EXISTS wallet_balance_kes,
    DROP COLUMN IF EXISTS auto_charge_paybill,
    DROP COLUMN IF EXISTS billing_phone;

-- Add lifecycle tracking columns
ALTER TABLE group_subscriptions
    ADD COLUMN IF NOT EXISTS grace_period_end     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspended_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dormant_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_reminder_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reminder_count       INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reactivated_count    INT NOT NULL DEFAULT 0,
    -- How many times this group has reactivated after suspension
    ADD COLUMN IF NOT EXISTS paid_through_date    DATE;
    -- Last date covered by a paid invoice

-- Recompute trial end dates for existing groups (use platform default of 30 days from creation)
UPDATE group_subscriptions gs
   SET trial_ends_at = COALESCE(
       gs.trial_ends_at,
       (SELECT g.created_at + INTERVAL '30 days' FROM groups g WHERE g.id = gs.group_id)
   )
 WHERE gs.status = 'TRIAL'
   AND gs.trial_ends_at IS NULL;

-- ── 4. LoanStatus: PENDING_DISBURSEMENT ──────────────────────────────────────
-- This state sits between APPROVED and ACTIVE:
--   APPROVED → (admin issues disbursement instruction) → PENDING_DISBURSEMENT
--   PENDING_DISBURSEMENT → (treasurer confirms M-Pesa send) → ACTIVE
--   PENDING_DISBURSEMENT → (instruction expires / cancelled) → APPROVED

ALTER TABLE loan_accounts
    DROP CONSTRAINT IF EXISTS loan_accounts_status_check;

ALTER TABLE loan_accounts
    ADD CONSTRAINT loan_accounts_status_check
    CHECK (status IN (
        'APPLICATION_SUBMITTED',
        'PENDING_GUARANTOR',
        'PENDING_APPROVAL',
        'APPROVED',
        'PENDING_DISBURSEMENT',  -- instruction issued, waiting for treasurer confirmation
        'ACTIVE',
        'DEFAULTED',
        'SETTLED',
        'WRITTEN_OFF',
        'REJECTED',
        'CANCELLED',
        'DISBURSED'              -- kept for backwards compat (same as PENDING_DISBURSEMENT)
    ));

-- ── 5. Subscription invoices — link to PesaLoop's own payment account ─────────
ALTER TABLE subscription_invoices
    ADD COLUMN IF NOT EXISTS payment_account  VARCHAR(30) DEFAULT 'PESALOOP_PAYBILL',
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(100);
    -- Group pays PesaLoop paybill with their group slug as account reference
    -- e.g. Paybill: 123456, Account: wanjiku-table-banking

-- ── 6. Subscription lifecycle events (audit) ─────────────────────────────────
CREATE TABLE subscription_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID        NOT NULL REFERENCES groups(id),
    event_type      VARCHAR(40) NOT NULL
                        CHECK (event_type IN (
                            'TRIAL_STARTED',
                            'TRIAL_ENDING_SOON',   -- reminder sent
                            'TRIAL_ENDED',
                            'GRACE_STARTED',
                            'GRACE_REMINDER_SENT',
                            'GRACE_ENDED',
                            'SUSPENDED',
                            'DORMANT',
                            'REACTIVATED',
                            'PLAN_UPGRADED',
                            'PLAN_DOWNGRADED',
                            'CANCELLED',
                            'INVOICE_GENERATED',
                            'INVOICE_PAID',
                            'INVOICE_OVERDUE'
                        )),
    from_status     VARCHAR(20),
    to_status       VARCHAR(20),
    triggered_by    VARCHAR(20) NOT NULL DEFAULT 'SYSTEM'
                        CHECK (triggered_by IN ('SYSTEM','ADMIN','SUPER_ADMIN','PAYMENT')),
    actor_id        UUID        REFERENCES users(id),
    notes           TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sub_events_group ON subscription_events(group_id, created_at DESC);

-- ── 7. View: groups requiring lifecycle action today ─────────────────────────
CREATE OR REPLACE VIEW v_subscription_lifecycle AS
SELECT
    gs.group_id,
    g.name                          AS group_name,
    g.slug                          AS group_slug,
    gs.plan_code,
    gs.status,
    gs.trial_ends_at,
    gs.grace_period_end,
    gs.suspended_at,
    gs.dormant_at,
    gs.paid_through_date,
    gs.last_reminder_sent_at,
    gs.reminder_count,
    -- Days remaining in current phase
    CASE gs.status
        WHEN 'TRIAL'  THEN EXTRACT(DAY FROM gs.trial_ends_at - NOW())
        WHEN 'GRACE'  THEN EXTRACT(DAY FROM gs.grace_period_end - NOW())
        ELSE NULL
    END                             AS days_remaining,
    -- Whether this group needs an action today
    CASE
        WHEN gs.status = 'TRIAL'
             AND gs.trial_ends_at < NOW()                           THEN 'START_GRACE'
        WHEN gs.status = 'GRACE'
             AND gs.grace_period_end < NOW()                        THEN 'SUSPEND'
        WHEN gs.status = 'SUSPENDED'
             AND gs.suspended_at < NOW() - (
                 SELECT (config_value::INT || ' days')::INTERVAL
                   FROM platform_config
                  WHERE config_key = 'dormant_after_suspended_days'
                    AND group_id IS NULL
             )                                                       THEN 'MARK_DORMANT'
        WHEN gs.status IN ('TRIAL','GRACE')                         THEN 'SEND_REMINDER'
        ELSE 'NO_ACTION'
    END                             AS required_action,
    -- Admin contact info for reminder SMS
    u.full_name                     AS admin_name,
    u.phone_number                  AS admin_phone
FROM group_subscriptions gs
JOIN groups g ON g.id = gs.group_id
LEFT JOIN members m ON m.group_id = gs.group_id AND m.role = 'ADMIN' AND m.status = 'ACTIVE'
LEFT JOIN users u ON u.id = m.user_id
WHERE gs.status IN ('TRIAL','GRACE','SUSPENDED')
  AND g.status = 'ACTIVE';
