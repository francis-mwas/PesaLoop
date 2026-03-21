-- ============================================================
-- V4__loan_reference_sequence_guarantor_release.sql
-- Loan reference number sequence and guarantor status extension
-- ============================================================

-- ── Loan reference counter (per group per year) ───────────────────────────────
-- e.g. group A in 2024 → LN-2024-0001, LN-2024-0042, etc.
CREATE TABLE loan_reference_counters (
    group_id    UUID NOT NULL REFERENCES groups(id),
    year        INT  NOT NULL,
    last_number INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (group_id, year)
);

-- Atomic increment function — prevents race conditions when two loans
-- are submitted simultaneously (returns the next number)
CREATE OR REPLACE FUNCTION next_loan_number(p_group_id UUID, p_year INT)
RETURNS INT AS $$
DECLARE
    v_next INT;
BEGIN
    INSERT INTO loan_reference_counters (group_id, year, last_number)
    VALUES (p_group_id, p_year, 1)
    ON CONFLICT (group_id, year)
    DO UPDATE SET last_number = loan_reference_counters.last_number + 1
    RETURNING last_number INTO v_next;
    RETURN v_next;
END;
$$ LANGUAGE plpgsql;

-- ── Extend loan_guarantors with RELEASED status ───────────────────────────────
ALTER TABLE loan_guarantors
    DROP CONSTRAINT IF EXISTS loan_guarantors_status_check;

ALTER TABLE loan_guarantors
    ADD CONSTRAINT loan_guarantors_status_check
    CHECK (status IN ('PENDING','ACCEPTED','DECLINED','RELEASED'));

-- ── Add application_note column back (missed in V2) ───────────────────────────
ALTER TABLE loan_accounts
    ADD COLUMN IF NOT EXISTS application_note TEXT;

ALTER TABLE loan_accounts
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- ── Index for concentration check query ──────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_loans_member_active
    ON loan_accounts(member_id, status)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_loans_group_active
    ON loan_accounts(group_id, status)
    WHERE status IN ('ACTIVE', 'DISBURSED');

-- ── Loan product: add repayment_frequency if missing ─────────────────────────
ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS repayment_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'
        CHECK (repayment_frequency IN (
            'FLAT_RATE','DAILY','WEEKLY','FORTNIGHTLY',
            'MONTHLY','QUARTERLY','ANNUALLY','CUSTOM'));

-- ── View: group loan book summary ────────────────────────────────────────────
CREATE OR REPLACE VIEW v_loan_book AS
SELECT
    la.group_id,
    la.id AS loan_id,
    la.loan_reference,
    la.status,
    la.principal_amount,
    la.principal_balance,
    la.accrued_interest,
    la.penalty_balance,
    (la.principal_balance + la.accrued_interest + la.penalty_balance) AS total_outstanding,
    la.disbursement_date,
    la.due_date,
    la.due_date < CURRENT_DATE AND la.status = 'ACTIVE' AS is_overdue,
    m.id AS member_id,
    m.member_number,
    u.full_name AS member_name,
    u.phone_number AS member_phone,
    lp.name AS product_name,
    lp.interest_type,
    lp.accrual_frequency
FROM loan_accounts la
JOIN members m ON m.id = la.member_id
JOIN users u ON u.id = m.user_id
JOIN loan_products lp ON lp.id = la.product_id;

-- ── View: member savings ledger ───────────────────────────────────────────────
CREATE OR REPLACE VIEW v_member_savings AS
SELECT
    m.group_id,
    m.id AS member_id,
    m.member_number,
    u.full_name,
    m.shares_owned,
    g.share_price_amount,
    m.shares_owned * g.share_price_amount AS shares_value,
    m.savings_balance,
    m.arrears_balance,
    m.fines_balance,
    -- Active loan summary
    COALESCE(SUM(la.principal_balance + la.accrued_interest), 0) AS total_loan_outstanding
FROM members m
JOIN users u ON u.id = m.user_id
JOIN groups g ON g.id = m.group_id
LEFT JOIN loan_accounts la ON la.member_id = m.id AND la.status = 'ACTIVE'
WHERE m.status = 'ACTIVE'
GROUP BY m.group_id, m.id, m.member_number, u.full_name,
         m.shares_owned, g.share_price_amount, m.savings_balance,
         m.arrears_balance, m.fines_balance;
