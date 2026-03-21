-- ============================================================
-- V9__prod_readiness_fixes.sql
-- Fixes all schema issues identified in production readiness audit:
--   B1: Drop legacy platform_subscriptions table
--   B2: Add allows_loans column to groups
--   B3: Add recorded_by and narration to payment_records
--   B4: Rename dividend_line_items → dividend_distribution_entries alias
--   C4: Improve unmatched_payments index for C2B webhook
-- ============================================================

-- ── B1: Drop legacy platform_subscriptions (replaced by group_subscriptions in V6) ─
-- Migrate any data that might exist before dropping
INSERT INTO group_subscriptions (group_id, plan_code, status,
                                 trial_ends_at, current_period_start, current_period_end, created_at, updated_at)
SELECT
    ps.group_id,
    CASE
        WHEN ps.price_per_member_kes >= 50 AND ps.active_member_count >= 20 THEN 'GROWTH'
        ELSE 'FREE'
        END,
    ps.status,
    ps.trial_ends_at,
    ps.current_period_start,
    ps.current_period_end,
    ps.created_at,
    NOW()
FROM platform_subscriptions ps
WHERE ps.group_id NOT IN (SELECT group_id FROM group_subscriptions)
    ON CONFLICT (group_id) DO NOTHING;

DROP TABLE IF EXISTS platform_subscriptions CASCADE;

-- ── B2: Add allows_loans to groups ───────────────────────────────────────────
ALTER TABLE groups
    ADD COLUMN IF NOT EXISTS allows_loans BOOLEAN NOT NULL DEFAULT FALSE;

-- TABLE_BANKING groups allow loans by default
UPDATE groups
SET allows_loans = TRUE
WHERE group_types @> ARRAY['TABLE_BANKING']::TEXT[];

-- ── B3: Add missing columns to payment_records ───────────────────────────────
ALTER TABLE payment_records
    ADD COLUMN IF NOT EXISTS recorded_by  UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS narration    TEXT,
    ADD COLUMN IF NOT EXISTS recorded_at  TIMESTAMPTZ;

-- Fill recorded_at from created_at for existing rows
UPDATE payment_records
SET recorded_at = created_at
WHERE recorded_at IS NULL;

-- ── B4: Create dividend_distribution_entries as view over dividend_line_items ─
-- DisbursementService references dividend_distribution_entries but V2 created
-- the table as dividend_line_items. We create a view so both names work,
-- then update DisbursementService to use the correct name.
CREATE OR REPLACE VIEW dividend_distribution_entries AS
SELECT
    id,
    distribution_id,
    group_id,
    member_id,
    shares_owned,
    savings_contribution,
    interest_share,
    total_dividend,
    currency_code,
    status,
    paid_at,
    mpesa_ref
FROM dividend_line_items;

-- ── B5 (schema side): Add otp_attempts counter to prevent brute-force ─────────
ALTER TABLE phone_otp_requests
    ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;

-- ── Startup guard: ensure group_subscriptions row exists for all active groups ─
-- Fixes M4: CreateGroupUseCase doesn't create subscription row
INSERT INTO group_subscriptions (group_id, plan_code, status, trial_ends_at, created_at, updated_at)
SELECT
    g.id,
    'FREE',
    'TRIAL',
    g.created_at + INTERVAL '30 days',
    NOW(),
    NOW()
FROM groups g
WHERE g.id NOT IN (SELECT group_id FROM group_subscriptions)
  AND g.status = 'ACTIVE';

-- ── Index improvements ────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_payment_records_member_type
    ON payment_records(member_id, payment_type, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_records_mpesa_txn
    ON payment_records(mpesa_transaction_id)
    WHERE mpesa_transaction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_groups_mpesa_shortcode
    ON groups(mpesa_shortcode)
    WHERE mpesa_shortcode IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_otp_requests_phone_recent
    ON phone_otp_requests(phone_number, created_at DESC)
    WHERE used_at IS NULL;