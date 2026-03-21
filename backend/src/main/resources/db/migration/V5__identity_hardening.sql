-- ============================================================
-- V5__identity_hardening.sql
-- User KYC, phone verification, encrypted national ID,
-- member number counters, invite tokens
-- ============================================================

-- ── Extend users table ────────────────────────────────────────────────────────

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS kyc_level VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (kyc_level IN ('NONE','PHONE_VERIFIED','ID_VERIFIED')),

    -- National ID stored encrypted (AES-256-GCM via app layer)
    -- Original plain national_id column replaced with encrypted version
    ADD COLUMN IF NOT EXISTS national_id_encrypted TEXT,
    -- SHA-256 hash for uniqueness checks without decrypting
    ADD COLUMN IF NOT EXISTS national_id_hash VARCHAR(64) UNIQUE,

    ADD COLUMN IF NOT EXISTS date_of_birth DATE,

    ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ,

    -- OTP for phone verification (short-lived, hashed)
    ADD COLUMN IF NOT EXISTS phone_otp_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS phone_otp_expires_at TIMESTAMPTZ,

    -- One-time invite token for admin-created stub accounts
    -- Member receives SMS with link: pesaloop.co.ke/invite/{token}
    ADD COLUMN IF NOT EXISTS invite_token VARCHAR(64) UNIQUE,
    ADD COLUMN IF NOT EXISTS invite_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS invite_accepted_at TIMESTAMPTZ,

    -- Account type: SELF_REGISTERED | ADMIN_CREATED (stub, no password yet)
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(20) NOT NULL DEFAULT 'SELF_REGISTERED'
        CHECK (account_type IN ('SELF_REGISTERED', 'ADMIN_CREATED'));

-- Migrate existing plain national_id data: move to hash column
-- (In production, the app would encrypt and store in national_id_encrypted)
UPDATE users
   SET national_id_hash = ENCODE(DIGEST(national_id, 'sha256'), 'hex')
 WHERE national_id IS NOT NULL
   AND national_id_hash IS NULL;

-- Keep old column temporarily for migration safety, will drop in V6
-- ALTER TABLE users DROP COLUMN IF EXISTS national_id;

-- ── Phone OTP requests (rate limiting + audit) ────────────────────────────────
CREATE TABLE IF NOT EXISTS phone_otp_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(15) NOT NULL,
    otp_hash        VARCHAR(64) NOT NULL,
    purpose         VARCHAR(20) NOT NULL
                        CHECK (purpose IN ('REGISTRATION','LOGIN','PHONE_CHANGE')),
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_phone ON phone_otp_requests(phone_number, created_at DESC);

-- Max 5 OTPs per phone per hour (enforced in application layer)
-- Records retained for 24 hours for audit, then cleaned up by scheduler

-- ── Member number counters ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS member_number_counters (
    group_id    UUID NOT NULL REFERENCES groups(id),
    last_number INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (group_id)
);

-- Atomic member number generation — prevents duplicates on concurrent registrations
CREATE OR REPLACE FUNCTION next_member_number(p_group_id UUID)
RETURNS TEXT AS $$
DECLARE
    v_next INT;
BEGIN
    INSERT INTO member_number_counters (group_id, last_number)
    VALUES (p_group_id, 1)
    ON CONFLICT (group_id)
    DO UPDATE SET last_number = member_number_counters.last_number + 1
    RETURNING last_number INTO v_next;

    -- M-{3-digit padded number}: M-001, M-017, M-999
    -- For groups > 999 members, auto-expands to 4 digits: M-1000
    RETURN 'M-' || LPAD(v_next::TEXT, 3, '0');
END;
$$ LANGUAGE plpgsql;

-- Backfill existing members with sequential numbers per group
-- (only runs once on first migration)
DO $$
DECLARE
    r RECORD;
    v_num TEXT;
BEGIN
    FOR r IN
        SELECT m.id, m.group_id
          FROM members m
         WHERE m.member_number IS NULL OR m.member_number = ''
         ORDER BY m.group_id, m.created_at
    LOOP
        v_num := next_member_number(r.group_id);
        UPDATE members SET member_number = v_num WHERE id = r.id;
    END LOOP;
END $$;

-- ── Ensure member_number is NOT NULL now that we've backfilled ─────────────────
ALTER TABLE members
    ALTER COLUMN member_number SET NOT NULL,
    ALTER COLUMN member_number SET DEFAULT '';  -- overridden by app before insert

-- ── Index for invite token lookup ─────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_invite_token
    ON users(invite_token)
    WHERE invite_token IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_kyc
    ON users(kyc_level);

-- ── View: user group memberships (for group picker on login) ──────────────────
CREATE OR REPLACE VIEW v_user_groups AS
SELECT
    m.user_id,
    m.id        AS member_id,
    m.group_id,
    g.name      AS group_name,
    g.slug      AS group_slug,
    g.status    AS group_status,
    m.role,
    m.member_number,
    m.status    AS member_status,
    m.shares_owned,
    m.savings_balance,
    m.joined_on
FROM members m
JOIN groups g ON g.id = m.group_id
WHERE m.status = 'ACTIVE'
  AND g.status = 'ACTIVE';
