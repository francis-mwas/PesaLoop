-- ============================================================
-- V2__contributions_loans_payouts.sql
-- Contribution cycles, entries, loan products, loan accounts,
-- repayment schedules, payouts
-- ============================================================

-- ── Contribution Cycles ───────────────────────────────────────────────────────
CREATE TABLE contribution_cycles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                UUID NOT NULL REFERENCES groups(id),
    cycle_number            INT NOT NULL,
    financial_year          INT NOT NULL,
    due_date                DATE NOT NULL,
    grace_period_end        DATE NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                                CHECK (status IN ('OPEN','GRACE_PERIOD','CLOSED','PAYOUT_READY','PAID_OUT')),

    -- Group-level totals (updated incrementally as payments arrive)
    total_expected_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_collected_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_arrears_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_fines_amount      NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency_code           VARCHAR(3) NOT NULL DEFAULT 'KES',

    -- MGR fields
    mgr_beneficiary_id      UUID REFERENCES members(id),
    mgr_payout_amount       NUMERIC(15,2),
    mgr_paid_out_at         TIMESTAMPTZ,
    mgr_mpesa_ref           VARCHAR(30),

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0,

    UNIQUE (group_id, cycle_number, financial_year)
);

CREATE INDEX idx_cycles_group_status ON contribution_cycles(group_id, status);
CREATE INDEX idx_cycles_due_date     ON contribution_cycles(due_date);
CREATE TRIGGER trg_cycles_updated_at BEFORE UPDATE ON contribution_cycles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Contribution Entries (per-member per-cycle) ───────────────────────────────
CREATE TABLE contribution_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                    UUID NOT NULL REFERENCES groups(id),
    cycle_id                    UUID NOT NULL REFERENCES contribution_cycles(id),
    member_id                   UUID NOT NULL REFERENCES members(id),

    expected_amount             NUMERIC(15,2) NOT NULL,     -- snapshot at cycle open
    paid_amount                 NUMERIC(15,2) NOT NULL DEFAULT 0,
    arrears_carried_forward     NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency_code               VARCHAR(3) NOT NULL DEFAULT 'KES',

    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','PARTIAL','PAID','LATE','WAIVED','ARREARS_APPLIED')),

    first_payment_at            TIMESTAMPTZ,
    fully_paid_at               TIMESTAMPTZ,
    last_payment_method         VARCHAR(25)
                                    CHECK (last_payment_method IN
                                        ('MPESA_STK_PUSH','MPESA_PAYBILL','MPESA_TILL',
                                         'CASH','BANK_TRANSFER','INTERNAL_TRANSFER')),
    last_mpesa_reference        VARCHAR(30),
    recorded_by                 UUID REFERENCES users(id),

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                     BIGINT NOT NULL DEFAULT 0,

    UNIQUE (cycle_id, member_id)
);

CREATE INDEX idx_entries_cycle     ON contribution_entries(cycle_id);
CREATE INDEX idx_entries_member    ON contribution_entries(member_id);
CREATE INDEX idx_entries_status    ON contribution_entries(group_id, status);
CREATE TRIGGER trg_entries_updated_at BEFORE UPDATE ON contribution_entries FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Payment Records (individual M-Pesa/cash transactions) ────────────────────
CREATE TABLE payment_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id            UUID NOT NULL REFERENCES groups(id),
    entry_id            UUID REFERENCES contribution_entries(id),  -- null for loan repayments
    loan_id             UUID,                                       -- FK added after loans table
    member_id           UUID NOT NULL REFERENCES members(id),
    payment_type        VARCHAR(20) NOT NULL
                            CHECK (payment_type IN ('CONTRIBUTION','LOAN_REPAYMENT','FINE_PAYMENT','PENALTY_PAYMENT')),
    amount              NUMERIC(15,2) NOT NULL,
    currency_code       VARCHAR(3) NOT NULL DEFAULT 'KES',
    payment_method      VARCHAR(25) NOT NULL,
    mpesa_reference     VARCHAR(30),
    mpesa_transaction_id VARCHAR(30) UNIQUE,   -- idempotency key for M-Pesa webhooks
    phone_number        VARCHAR(15),
    narration           VARCHAR(200),
    status              VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                            CHECK (status IN ('PENDING','COMPLETED','FAILED','REVERSED')),
    recorded_by         UUID REFERENCES users(id),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_group        ON payment_records(group_id, recorded_at DESC);
CREATE INDEX idx_payments_member       ON payment_records(member_id);
CREATE INDEX idx_payments_mpesa_ref    ON payment_records(mpesa_transaction_id) WHERE mpesa_transaction_id IS NOT NULL;

-- ── Loan Products ─────────────────────────────────────────────────────────────
CREATE TABLE loan_products (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                        UUID NOT NULL REFERENCES groups(id),
    name                            VARCHAR(80) NOT NULL,
    description                     TEXT,
    active                          BOOLEAN NOT NULL DEFAULT TRUE,

    -- Interest
    interest_type                   VARCHAR(20) NOT NULL DEFAULT 'FLAT'
                                        CHECK (interest_type IN ('FLAT','REDUCING_BALANCE','SIMPLE_INTEREST')),
    accrual_frequency               VARCHAR(20) NOT NULL DEFAULT 'FLAT_RATE'
                                        CHECK (accrual_frequency IN
                                            ('FLAT_RATE','DAILY','WEEKLY','FORTNIGHTLY',
                                             'MONTHLY','QUARTERLY','ANNUALLY','CUSTOM')),
    interest_rate                   NUMERIC(8,6) NOT NULL,      -- e.g. 0.100000 = 10%
    custom_accrual_interval_days    INT,

    -- Loan size
    minimum_amount                  NUMERIC(15,2) NOT NULL DEFAULT 0,
    maximum_amount                  NUMERIC(15,2) NOT NULL,
    max_multiple_of_savings         NUMERIC(6,2),               -- e.g. 3.00
    max_multiple_of_shares_value    NUMERIC(6,2),

    -- Repayment
    max_repayment_periods           INT NOT NULL DEFAULT 3,
    repayment_frequency             VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    bullet_repayment                BOOLEAN NOT NULL DEFAULT FALSE,

    -- Eligibility
    minimum_membership_months       INT NOT NULL DEFAULT 0,
    minimum_shares_owned            INT NOT NULL DEFAULT 1,
    requires_guarantor              BOOLEAN NOT NULL DEFAULT FALSE,
    max_guarantors                  INT NOT NULL DEFAULT 1,
    requires_zero_arrears           BOOLEAN NOT NULL DEFAULT TRUE,
    max_concurrent_loans            INT NOT NULL DEFAULT 1,

    -- Penalty
    late_repayment_penalty_rate     NUMERIC(6,4) NOT NULL DEFAULT 0.05,  -- 5%
    penalty_grace_period_days       INT NOT NULL DEFAULT 3,

    created_by                      UUID REFERENCES users(id),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_loan_products_group ON loan_products(group_id, active);
CREATE TRIGGER trg_loan_products_updated_at BEFORE UPDATE ON loan_products FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Loan Accounts ─────────────────────────────────────────────────────────────
CREATE TABLE loan_accounts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                    UUID NOT NULL REFERENCES groups(id),
    member_id                   UUID NOT NULL REFERENCES members(id),
    product_id                  UUID NOT NULL REFERENCES loan_products(id),
    loan_reference              VARCHAR(20) NOT NULL,   -- e.g. "LN-2024-0042"
    status                      VARCHAR(25) NOT NULL DEFAULT 'APPLICATION_SUBMITTED'
                                    CHECK (status IN (
                                        'APPLICATION_SUBMITTED','PENDING_GUARANTOR',
                                        'PENDING_APPROVAL','APPROVED','DISBURSED','ACTIVE',
                                        'SETTLED','DEFAULTED','WRITTEN_OFF','REJECTED','CANCELLED')),

    -- Original terms (immutable after disbursement)
    principal_amount            NUMERIC(15,2) NOT NULL,
    total_interest_charged      NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency_code               VARCHAR(3) NOT NULL DEFAULT 'KES',
    disbursement_date           DATE,
    due_date                    DATE,

    -- Running balances
    principal_balance           NUMERIC(15,2) NOT NULL DEFAULT 0,
    accrued_interest            NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_interest_repaid       NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_principal_repaid      NUMERIC(15,2) NOT NULL DEFAULT 0,
    penalty_balance             NUMERIC(15,2) NOT NULL DEFAULT 0,

    -- Application
    application_note            TEXT,
    rejection_reason            TEXT,

    -- Disbursement
    disbursement_mpesa_ref      VARCHAR(30),
    disbursed_at                TIMESTAMPTZ,
    disbursed_by                UUID REFERENCES users(id),
    approved_by                 UUID REFERENCES users(id),
    approved_at                 TIMESTAMPTZ,

    -- Settlement
    settled_at                  TIMESTAMPTZ,
    settlement_note             TEXT,

    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                     BIGINT NOT NULL DEFAULT 0,

    UNIQUE (group_id, loan_reference)
);

CREATE INDEX idx_loans_group_status ON loan_accounts(group_id, status);
CREATE INDEX idx_loans_member       ON loan_accounts(member_id);
CREATE INDEX idx_loans_due_date     ON loan_accounts(due_date) WHERE status = 'ACTIVE';
CREATE TRIGGER trg_loans_updated_at BEFORE UPDATE ON loan_accounts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add FK from payment_records → loan_accounts (now that loans table exists)
ALTER TABLE payment_records ADD CONSTRAINT fk_payment_loan
    FOREIGN KEY (loan_id) REFERENCES loan_accounts(id);

-- ── Loan Guarantors ───────────────────────────────────────────────────────────
CREATE TABLE loan_guarantors (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID NOT NULL REFERENCES loan_accounts(id),
    group_id            UUID NOT NULL REFERENCES groups(id),
    guarantor_member_id UUID NOT NULL REFERENCES members(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','ACCEPTED','DECLINED')),
    responded_at        TIMESTAMPTZ,
    response_note       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (loan_id, guarantor_member_id)
);

-- ── Repayment Installments (amortization schedule) ────────────────────────────
CREATE TABLE repayment_installments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID NOT NULL REFERENCES loan_accounts(id),
    group_id            UUID NOT NULL REFERENCES groups(id),
    installment_number  INT NOT NULL,
    due_date            DATE NOT NULL,

    principal_due       NUMERIC(15,2) NOT NULL,
    interest_due        NUMERIC(15,2) NOT NULL,
    total_due           NUMERIC(15,2) NOT NULL,
    balance_after       NUMERIC(15,2) NOT NULL,

    principal_paid      NUMERIC(15,2) NOT NULL DEFAULT 0,
    interest_paid       NUMERIC(15,2) NOT NULL DEFAULT 0,
    penalty_paid        NUMERIC(15,2) NOT NULL DEFAULT 0,

    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PARTIAL','PAID','OVERDUE','WAIVED')),
    paid_at             TIMESTAMPTZ,
    mpesa_ref           VARCHAR(30),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (loan_id, installment_number)
);

CREATE INDEX idx_installments_loan     ON repayment_installments(loan_id);
CREATE INDEX idx_installments_due_date ON repayment_installments(due_date) WHERE status IN ('PENDING','PARTIAL','OVERDUE');
CREATE TRIGGER trg_installments_updated_at BEFORE UPDATE ON repayment_installments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Year-End Dividends ────────────────────────────────────────────────────────
CREATE TABLE dividend_distributions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                UUID NOT NULL REFERENCES groups(id),
    financial_year          INT NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PREVIEW'
                                CHECK (status IN ('PREVIEW','APPROVED','DISBURSING','COMPLETED','CANCELLED')),
    total_savings_pool      NUMERIC(15,2) NOT NULL,
    total_interest_pool     NUMERIC(15,2) NOT NULL,
    total_distributable     NUMERIC(15,2) NOT NULL,
    total_group_shares      INT NOT NULL,
    currency_code           VARCHAR(3) NOT NULL DEFAULT 'KES',
    approved_by             UUID REFERENCES users(id),
    approved_at             TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_by              UUID REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, financial_year)
);

CREATE TABLE dividend_line_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distribution_id         UUID NOT NULL REFERENCES dividend_distributions(id),
    group_id                UUID NOT NULL REFERENCES groups(id),
    member_id               UUID NOT NULL REFERENCES members(id),
    shares_owned            INT NOT NULL,
    savings_contribution    NUMERIC(15,2) NOT NULL,
    interest_share          NUMERIC(15,2) NOT NULL,
    total_dividend          NUMERIC(15,2) NOT NULL,
    currency_code           VARCHAR(3) NOT NULL DEFAULT 'KES',
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','PAID','FAILED')),
    mpesa_ref               VARCHAR(30),
    paid_at                 TIMESTAMPTZ,
    UNIQUE (distribution_id, member_id)
);

CREATE INDEX idx_dividend_distribution ON dividend_line_items(distribution_id);
