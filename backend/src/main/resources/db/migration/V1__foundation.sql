-- ============================================================
-- V1__foundation.sql
-- PesaLoop — Foundation Schema
-- Users, Groups, Members, Share Config
-- ============================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- for name search

-- ── Users (Identity context) ──────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(15)  NOT NULL UNIQUE,
    email           VARCHAR(255) UNIQUE,
    full_name       VARCHAR(100) NOT NULL,
    national_id     VARCHAR(20)  UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','SUSPENDED','LOCKED')),
    phone_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_national_id ON users(national_id) WHERE national_id IS NOT NULL;

-- ── Groups (Chamaa) ────────────────────────────────────────────────────────────
CREATE TABLE groups (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                        VARCHAR(80)  NOT NULL UNIQUE,
    name                        VARCHAR(100) NOT NULL,
    description                 TEXT,
    status                      VARCHAR(20)  NOT NULL DEFAULT 'PENDING_SETUP'
                                    CHECK (status IN ('PENDING_SETUP','ACTIVE','SUSPENDED','CLOSED')),
    currency_code               VARCHAR(3)   NOT NULL DEFAULT 'KES',

    -- Share configuration
    share_price_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,   -- e.g. 3000.00
    share_price_currency        VARCHAR(3)   NOT NULL DEFAULT 'KES',
    minimum_shares              INT NOT NULL DEFAULT 1,
    maximum_shares              INT NOT NULL DEFAULT 2147483647,     -- effectively unlimited
    shares_mode                 BOOLEAN NOT NULL DEFAULT TRUE,       -- false = flat amount mode
    allow_share_change_mid_year BOOLEAN NOT NULL DEFAULT FALSE,

    -- Contribution schedule
    contribution_frequency      VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY'
                                    CHECK (contribution_frequency IN
                                        ('DAILY','WEEKLY','FORTNIGHTLY','MONTHLY',
                                         'QUARTERLY','ANNUALLY','CUSTOM')),
    custom_frequency_days       INT,                                 -- only when CUSTOM

    -- Group types (stored as array — group can be multiple types)
    group_types                 TEXT[] NOT NULL DEFAULT '{TABLE_BANKING}',

    -- Financial year
    financial_year_start_month  INT NOT NULL DEFAULT 1 CHECK (financial_year_start_month BETWEEN 1 AND 12),
    financial_year_start_day    INT NOT NULL DEFAULT 1 CHECK (financial_year_start_day BETWEEN 1 AND 28),

    -- M-Pesa
    mpesa_shortcode             VARCHAR(20),
    mpesa_shortcode_type        VARCHAR(10) CHECK (mpesa_shortcode_type IN ('PAYBILL','TILL')),

    -- Operational settings
    grace_period_days           INT NOT NULL DEFAULT 3,
    requires_guarantor_for_loans BOOLEAN NOT NULL DEFAULT TRUE,
    max_active_loans_per_member INT NOT NULL DEFAULT 2,

    -- MGR settings
    mgr_rotation_strategy       VARCHAR(20) DEFAULT 'FIXED_ORDER'
                                    CHECK (mgr_rotation_strategy IN
                                        ('FIXED_ORDER','RANDOM_DRAW','JOINING_ORDER','BID','SENIORITY')),
    mgr_payout_trigger          VARCHAR(20) DEFAULT 'ALL_MEMBERS_PAID'
                                    CHECK (mgr_payout_trigger IN
                                        ('ALL_MEMBERS_PAID','DUE_DATE_REACHED','MANUAL_APPROVAL')),
    mgr_wait_for_all            BOOLEAN NOT NULL DEFAULT TRUE,
    mgr_allow_position_swaps    BOOLEAN NOT NULL DEFAULT FALSE,

    -- Registration
    registration_number         VARCHAR(50),
    physical_address            TEXT,
    county                      VARCHAR(50),

    -- Audit
    created_by                  UUID NOT NULL REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_groups_slug    ON groups(slug);
CREATE INDEX idx_groups_status  ON groups(status);

-- ── Members ───────────────────────────────────────────────────────────────────
CREATE TABLE members (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                    UUID NOT NULL REFERENCES groups(id),
    user_id                     UUID NOT NULL REFERENCES users(id),
    member_number               VARCHAR(20) NOT NULL,   -- e.g. "M-001"

    role                        VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
                                    CHECK (role IN ('ADMIN','TREASURER','SECRETARY','MEMBER','AUDITOR')),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                    CHECK (status IN ('ACTIVE','DORMANT','SUSPENDED','EXITED','DECEASED')),

    -- Share ownership
    shares_owned                INT NOT NULL DEFAULT 1,
    shares_last_changed_on      DATE,

    -- Override: when not null, this amount is used instead of shares × price
    custom_contribution_amount  NUMERIC(15,2),
    custom_contribution_currency VARCHAR(3),

    -- Running balances (denormalized, kept in sync via triggers / domain events)
    savings_balance             NUMERIC(15,2) NOT NULL DEFAULT 0,
    arrears_balance             NUMERIC(15,2) NOT NULL DEFAULT 0,
    fines_balance               NUMERIC(15,2) NOT NULL DEFAULT 0,

    -- Personal details
    joined_on                   DATE NOT NULL DEFAULT CURRENT_DATE,
    national_id                 VARCHAR(20),
    phone_number                VARCHAR(15),

    -- Next of kin
    next_of_kin_name            VARCHAR(100),
    next_of_kin_phone           VARCHAR(15),
    next_of_kin_relationship    VARCHAR(50),

    -- MGR position
    mgr_position                INT,
    mgr_slot_status             VARCHAR(10) CHECK (mgr_slot_status IN ('PENDING','SERVED','SKIPPED')),

    -- Audit
    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                     BIGINT NOT NULL DEFAULT 0,

    UNIQUE (group_id, user_id),
    UNIQUE (group_id, member_number)
);

CREATE INDEX idx_members_group_id  ON members(group_id);
CREATE INDEX idx_members_user_id   ON members(user_id);
CREATE INDEX idx_members_status    ON members(group_id, status);

-- ── Share Config History (audit trail for share price changes) ─────────────────
CREATE TABLE share_config_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id            UUID NOT NULL REFERENCES groups(id),
    share_price_amount  NUMERIC(15,2) NOT NULL,
    minimum_shares      INT NOT NULL,
    maximum_shares      INT NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE,               -- null = currently active
    changed_by          UUID NOT NULL REFERENCES users(id),
    change_reason       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_share_history_group ON share_config_history(group_id, effective_from DESC);

-- ── Member Share Changes (individual member share updates) ────────────────────
CREATE TABLE member_share_changes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES groups(id),
    member_id       UUID NOT NULL REFERENCES members(id),
    shares_before   INT NOT NULL,
    shares_after    INT NOT NULL,
    effective_date  DATE NOT NULL,
    approved_by     UUID REFERENCES users(id),
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Audit Log (non-deletable, append-only) ────────────────────────────────────
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    group_id        UUID REFERENCES groups(id),
    actor_id        UUID REFERENCES users(id),
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID,
    action          VARCHAR(50) NOT NULL,
    before_state    JSONB,
    after_state     JSONB,
    ip_address      INET,
    user_agent      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_group    ON audit_log(group_id, created_at DESC);
CREATE INDEX idx_audit_log_entity   ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_actor    ON audit_log(actor_id);

-- Prevent deletes and updates on audit_log
CREATE RULE audit_log_no_update AS ON UPDATE TO audit_log DO INSTEAD NOTHING;
CREATE RULE audit_log_no_delete AS ON DELETE TO audit_log DO INSTEAD NOTHING;

-- ── Auto-update updated_at ────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at   BEFORE UPDATE ON users   FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_groups_updated_at  BEFORE UPDATE ON groups  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_members_updated_at BEFORE UPDATE ON members FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
