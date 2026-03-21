-- ============================================================
-- V3__payments_notifications.sql
-- STK push requests, unmatched payments, notifications
-- ============================================================

-- ── STK Push Requests (tracks initiated STK pushes for callback matching) ─────
CREATE TABLE stk_push_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                UUID NOT NULL REFERENCES groups(id),
    member_id               UUID NOT NULL REFERENCES members(id),
    entry_id                UUID REFERENCES contribution_entries(id),  -- null for loan repayments
    loan_id                 UUID REFERENCES loan_accounts(id),
    merchant_request_id     VARCHAR(50) UNIQUE,     -- M-Pesa's MerchantRequestID
    checkout_request_id     VARCHAR(50) UNIQUE,     -- M-Pesa's CheckoutRequestID (match key)
    phone_number            VARCHAR(15) NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,
    purpose                 VARCHAR(20) NOT NULL    -- CONTRIBUTION | LOAN_REPAYMENT
                                CHECK (purpose IN ('CONTRIBUTION','LOAN_REPAYMENT')),
    status                  VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','SUCCESS','FAILED','TIMEOUT')),
    failure_reason          TEXT,
    initiated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '5 minutes'
);

CREATE INDEX idx_stk_checkout_id ON stk_push_requests(checkout_request_id);
CREATE INDEX idx_stk_merchant_id ON stk_push_requests(merchant_request_id);
CREATE INDEX idx_stk_status      ON stk_push_requests(status, expires_at);

-- Auto-expire timed-out STK pushes (run by scheduler)
-- Managed by the OverdueCheckScheduler — no DB trigger needed

-- ── Unmatched Payments (C2B payments we couldn't auto-match) ──────────────────
CREATE TABLE unmatched_payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mpesa_transaction_id VARCHAR(30) UNIQUE NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    phone_number        VARCHAR(15),
    bill_ref_number     VARCHAR(50),
    business_short_code VARCHAR(20),
    resolved            BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by         UUID REFERENCES users(id),
    resolved_at         TIMESTAMPTZ,
    resolution_note     TEXT,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Notification Log ──────────────────────────────────────────────────────────
CREATE TABLE notification_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID REFERENCES groups(id),
    member_id       UUID REFERENCES members(id),
    channel         VARCHAR(10) NOT NULL CHECK (channel IN ('SMS','WHATSAPP','IN_APP','EMAIL')),
    recipient       VARCHAR(255) NOT NULL,  -- phone or email
    template_key    VARCHAR(80) NOT NULL,   -- e.g. CONTRIBUTION_REMINDER, LOAN_APPROVED
    message_body    TEXT NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'SENT'
                        CHECK (status IN ('SENT','DELIVERED','FAILED','PENDING')),
    provider_ref    VARCHAR(80),            -- Africa's Talking message ID
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at    TIMESTAMPTZ,
    error_message   TEXT
);

CREATE INDEX idx_notifications_group  ON notification_log(group_id, sent_at DESC);
CREATE INDEX idx_notifications_member ON notification_log(member_id);

-- ── SMS Wallet (per group SMS balance) ────────────────────────────────────────
CREATE TABLE sms_wallets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL UNIQUE REFERENCES groups(id),
    balance_units   INT NOT NULL DEFAULT 150,   -- free units on signup
    total_purchased INT NOT NULL DEFAULT 0,
    total_used      INT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Platform Subscriptions ────────────────────────────────────────────────────
CREATE TABLE platform_subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id            UUID NOT NULL UNIQUE REFERENCES groups(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'TRIAL'
                            CHECK (status IN ('TRIAL','ACTIVE','GRACE','SUSPENDED','CANCELLED')),
    trial_ends_at       TIMESTAMPTZ,
    current_period_start DATE,
    current_period_end   DATE,
    price_per_member_kes NUMERIC(8,2) NOT NULL DEFAULT 50.00,
    active_member_count  INT NOT NULL DEFAULT 0,
    amount_due           NUMERIC(15,2) NOT NULL DEFAULT 0,
    amount_paid          NUMERIC(15,2) NOT NULL DEFAULT 0,
    last_invoice_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_subscriptions_updated_at
    BEFORE UPDATE ON platform_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
