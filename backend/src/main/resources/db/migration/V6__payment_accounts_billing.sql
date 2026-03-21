-- ============================================================
-- V6__payment_accounts_billing.sql
-- Group payment accounts (M-Pesa paybill, till, bank accounts)
-- Subscription plans and billing
-- ============================================================

-- ── Group Payment Accounts ────────────────────────────────────────────────────
-- A group can have multiple payment accounts:
--   - An M-Pesa Paybill (for receiving contributions via pay bill)
--   - An M-Pesa Till (buy goods)
--   - A bank account (for large transfers, diaspora payments)
--   - A B2C shortcode (for disbursements — may be same as paybill)
--
-- Each account has a PROVIDER (who runs the rails) and a TYPE (what it does).
-- This separation is important: Equity Bank is the PROVIDER, "Bank Account" is the TYPE.
-- Safaricom is the PROVIDER, "M-Pesa Paybill" is the TYPE.

CREATE TABLE group_payment_accounts (
                                        id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        group_id            UUID NOT NULL REFERENCES groups(id),

    -- What kind of account is this?
                                        account_type        VARCHAR(30) NOT NULL
                                            CHECK (account_type IN (
                                                                    'MPESA_PAYBILL',      -- Lipa na M-Pesa paybill — members pay using M-Pesa
                                                                    'MPESA_TILL',         -- Buy Goods till number
                                                                    'MPESA_B2C',          -- Outbound disbursements (may share shortcode with paybill)
                                                                    'BANK_ACCOUNT',       -- Regular bank account (Equity, KCB, Co-op, NCBA, etc.)
                                                                    'BANK_PAYBILL',       -- Some banks issue their own paybill numbers
                                                                    'PESALINK'            -- PesaLink instant interbank transfers
                                                )),

    -- The financial institution or provider
                                        provider            VARCHAR(50) NOT NULL
                                            CHECK (provider IN (
                                                -- Mobile money
                                                                'SAFARICOM_MPESA',
                                                                'AIRTEL_MONEY',
                                                                'TKASH',              -- Telkom Kenya
                                                -- Banks
                                                                'EQUITY_BANK',
                                                                'KCB',
                                                                'COOPERATIVE_BANK',
                                                                'NCBA',
                                                                'ABSA',
                                                                'STANBIC',
                                                                'FAMILY_BANK',
                                                                'DTB',                -- Diamond Trust Bank
                                                                'I_AND_M',
                                                                'SIDIAN_BANK',
                                                                'PESALINK',
                                                                'OTHER_BANK'
                                                )),

    -- The account identifier (what the payer uses)
                                        account_number      VARCHAR(50) NOT NULL,
    -- Examples:
    --   MPESA_PAYBILL: "522533" (the paybill number)
    --   MPESA_TILL:    "891234" (the till number)
    --   BANK_ACCOUNT:  "0111234567890" (account number)
    --   BANK_PAYBILL:  "200999" (bank's paybill — e.g. Equity 247247)

                                        account_name        VARCHAR(100) NOT NULL,
    -- Examples:
    --   "Wanjiku Welfare Group"
    --   "Nairobi Table Banking - KCB 0111234567890"

    -- For bank accounts: branch and sort code
                                        bank_branch         VARCHAR(100),
                                        bank_swift_code     VARCHAR(11),    -- for international transfers (diaspora groups)
                                        bank_sort_code      VARCHAR(20),

    -- For M-Pesa: the passkey and credentials
    -- Stored encrypted — only app layer can decrypt
                                        mpesa_passkey_encrypted     TEXT,
                                        mpesa_consumer_key_encrypted TEXT,
                                        mpesa_consumer_secret_encrypted TEXT,

    -- C2B URL registration status (M-Pesa paybills only)
                                        c2b_registered      BOOLEAN NOT NULL DEFAULT FALSE,
                                        c2b_registered_at   TIMESTAMPTZ,

    -- Account purpose: what is this account used FOR?
    -- A group may have one account for receiving contributions
    -- and a different shortcode for sending disbursements
                                        is_collection       BOOLEAN NOT NULL DEFAULT TRUE,    -- receives money IN
                                        is_disbursement     BOOLEAN NOT NULL DEFAULT FALSE,   -- sends money OUT
                                        is_primary          BOOLEAN NOT NULL DEFAULT FALSE,   -- default for this purpose

    -- Account status
                                        status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                            CHECK (status IN ('ACTIVE','INACTIVE','PENDING_VERIFICATION','SUSPENDED')),

    -- Human-readable display info
                                        display_label       VARCHAR(100),
    -- E.g. "M-Pesa Paybill 522533 — for monthly contributions"
    --      "Equity Bank — for large transfers and diaspora"

    -- Audit
                                        created_by          UUID REFERENCES users(id),
                                        verified_by         UUID REFERENCES users(id),
                                        verified_at         TIMESTAMPTZ,
                                        created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                        updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                        version             BIGINT NOT NULL DEFAULT 0

    -- A group can only have one primary collection account and one primary disbursement account
    -- (enforced via partial unique index below — NULLS NOT DISTINCT requires PG15+)
);

-- Enforce one primary collection and one primary disbursement per group (PG14-compatible)
CREATE UNIQUE INDEX idx_payment_accounts_primary_collection
    ON group_payment_accounts(group_id, is_collection)
    WHERE is_primary = TRUE;
CREATE UNIQUE INDEX idx_payment_accounts_primary_disbursement
    ON group_payment_accounts(group_id, is_disbursement)
    WHERE is_primary = TRUE;
CREATE INDEX idx_payment_accounts_group       ON group_payment_accounts(group_id, status);
CREATE INDEX idx_payment_accounts_type        ON group_payment_accounts(account_type, provider);
CREATE INDEX idx_payment_accounts_number      ON group_payment_accounts(account_number);
CREATE TRIGGER trg_payment_accounts_updated
    BEFORE UPDATE ON group_payment_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Subscription Plans ────────────────────────────────────────────────────────
CREATE TABLE subscription_plans (
                                    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    code                    VARCHAR(20) NOT NULL UNIQUE,
    -- FREE | GROWTH | PRO | ENTERPRISE
                                    name                    VARCHAR(50) NOT NULL,
                                    monthly_fee_kes         NUMERIC(10,2) NOT NULL DEFAULT 0,
                                    annual_fee_kes          NUMERIC(10,2),           -- null = not available annually
                                    max_members             INT,                     -- null = unlimited
                                    max_active_loans        INT,                     -- null = unlimited
                                    max_loan_products       INT,                     -- null = unlimited
                                    included_sms_per_month  INT,                     -- null = unlimited
                                    price_per_extra_sms_kes NUMERIC(6,2) NOT NULL DEFAULT 1.50,
                                    b2c_fee_rate            NUMERIC(6,4) NOT NULL DEFAULT 0,
    -- 0.005 = 0.5%, 0.003 = 0.3%
                                    b2c_fee_cap_kes         NUMERIC(10,2),           -- max fee per disbursement
                                    features                JSONB NOT NULL DEFAULT '[]',
    -- Array of feature codes: ["LOANS","DIVIDENDS","MGR_PAYOUT","WELFARE","REPORTS_ADVANCED","API","SACCO"]
                                    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
                                    sort_order              INT NOT NULL DEFAULT 0,
                                    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed the four plans
INSERT INTO subscription_plans (
    id, code, name, monthly_fee_kes, annual_fee_kes,
    max_members, max_active_loans, max_loan_products,
    included_sms_per_month, price_per_extra_sms_kes,
    b2c_fee_rate, b2c_fee_cap_kes, features, sort_order
) VALUES
      (
          gen_random_uuid(), 'FREE', 'Starter — Free', 0, NULL,
          20, 0, 0,
          50, 1.50,
          0, NULL,
          '["CONTRIBUTIONS","MANUAL_ENTRY","PAYBILL","MGR_BASIC","STATEMENTS_BASIC"]',
          1
      ),
      (
          gen_random_uuid(), 'GROWTH', 'Growth', 500, 5000,
          NULL, NULL, 3,
          200, 1.50,
          0.005, 500,
          '["CONTRIBUTIONS","MANUAL_ENTRY","PAYBILL","MGR_PAYOUT","LOANS","DIVIDENDS","WELFARE","FINES","REPORTS_BASIC","STATEMENTS_FULL","EXPORT_PDF","EXPORT_EXCEL"]',
          2
      ),
      (
          gen_random_uuid(), 'PRO', 'Pro', 1500, 15000,
          NULL, NULL, NULL,
          500, 1.00,
          0.003, 300,
          '["CONTRIBUTIONS","MANUAL_ENTRY","PAYBILL","MGR_PAYOUT","LOANS","DIVIDENDS","WELFARE","FINES","REPORTS_ADVANCED","STATEMENTS_FULL","EXPORT_PDF","EXPORT_EXCEL","API","BULK_B2C","CUSTOM_SMS_SENDER","INVESTMENT_KITTY","MULTI_CURRENCY","SACCO_BASIC"]',
          3
      ),
      (
          gen_random_uuid(), 'ENTERPRISE', 'Enterprise', 0, NULL,
          NULL, NULL, NULL,
          NULL, 0.80,
          0.002, 200,
          '["ALL"]',
          4
      );

-- ── Group Subscriptions ───────────────────────────────────────────────────────
CREATE TABLE group_subscriptions (
                                     id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     group_id                UUID NOT NULL UNIQUE REFERENCES groups(id),
                                     plan_code               VARCHAR(20) NOT NULL REFERENCES subscription_plans(code),
                                     billing_cycle           VARCHAR(10) NOT NULL DEFAULT 'MONTHLY'
                                         CHECK (billing_cycle IN ('MONTHLY','ANNUAL')),
                                     status                  VARCHAR(20) NOT NULL DEFAULT 'TRIAL'
                                         CHECK (status IN ('TRIAL','ACTIVE','GRACE','SUSPENDED','CANCELLED')),
                                     trial_ends_at           TIMESTAMPTZ,
                                     current_period_start    DATE,
                                     current_period_end      DATE,

    -- Billing wallet — group tops this up to pay invoices
                                     wallet_balance_kes      NUMERIC(15,2) NOT NULL DEFAULT 0,

    -- Auto-billing settings
                                     auto_charge_paybill     BOOLEAN NOT NULL DEFAULT FALSE,
    -- If true, system auto-charges the group's primary collection account monthly
                                     billing_phone           VARCHAR(15),
    -- Phone for STK Push billing if auto_charge_paybill = true

                                     last_invoice_at         TIMESTAMPTZ,
                                     next_invoice_at         TIMESTAMPTZ,
                                     cancelled_at            TIMESTAMPTZ,
                                     cancellation_reason     TEXT,

                                     created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                     updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_status    ON group_subscriptions(status, next_invoice_at);
CREATE TRIGGER trg_subscriptions2_updated
    BEFORE UPDATE ON group_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Subscription Invoices ─────────────────────────────────────────────────────
CREATE TABLE subscription_invoices (
                                       id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       group_id            UUID NOT NULL REFERENCES groups(id),
                                       plan_code           VARCHAR(20) NOT NULL,
                                       period_start        DATE NOT NULL,
                                       period_end          DATE NOT NULL,
                                       base_fee_kes        NUMERIC(10,2) NOT NULL,
                                       sms_charges_kes     NUMERIC(10,2) NOT NULL DEFAULT 0,
                                       b2c_fee_kes         NUMERIC(10,2) NOT NULL DEFAULT 0,
                                       total_kes           NUMERIC(10,2) NOT NULL,
                                       status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                           CHECK (status IN ('PENDING','PAID','WAIVED','OVERDUE','VOID')),
                                       paid_at             TIMESTAMPTZ,
                                       mpesa_ref           VARCHAR(30),
                                       created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_group  ON subscription_invoices(group_id, created_at DESC);
CREATE INDEX idx_invoices_status ON subscription_invoices(status, created_at);

-- ── Migrate existing groups: assign Free plan ─────────────────────────────────
INSERT INTO group_subscriptions (group_id, plan_code, status, trial_ends_at, created_at, updated_at)
SELECT id, 'FREE', 'ACTIVE', NOW() + INTERVAL '30 days', NOW(), NOW()
FROM groups
WHERE id NOT IN (SELECT group_id FROM group_subscriptions);

-- ── Migrate existing mpesa_shortcode to payment accounts ─────────────────────
INSERT INTO group_payment_accounts (
    id, group_id, account_type, provider, account_number, account_name,
    is_collection, is_disbursement, is_primary, status,
    c2b_registered, created_at, updated_at, version
)
SELECT
    gen_random_uuid(),
    id,
    CASE mpesa_shortcode_type WHEN 'TILL' THEN 'MPESA_TILL' ELSE 'MPESA_PAYBILL' END,
    'SAFARICOM_MPESA',
    mpesa_shortcode,
    name || ' — M-Pesa',
    TRUE, TRUE, TRUE, 'ACTIVE',
    TRUE,
    NOW(), NOW(), 0
FROM groups
WHERE mpesa_shortcode IS NOT NULL;

-- ── Feature access helper function ───────────────────────────────────────────
-- Returns TRUE if the group's current plan includes the given feature code
CREATE OR REPLACE FUNCTION group_has_feature(p_group_id UUID, p_feature VARCHAR)
RETURNS BOOLEAN AS $$
SELECT EXISTS (
    SELECT 1
    FROM group_subscriptions gs
             JOIN subscription_plans sp ON sp.code = gs.plan_code
    WHERE gs.group_id = p_group_id
      AND gs.status IN ('TRIAL','ACTIVE','GRACE')
      AND (
        sp.features @> to_jsonb(p_feature)
            OR sp.features @> '"ALL"'::jsonb
        )
);
$$ LANGUAGE sql STABLE;