-- ============================================================
-- V11__add_audit_columns.sql
-- Add ALL columns that JPA entities map but DB tables lack.
-- Found by cross-checking every @Column in every *JpaEntity
-- against the actual CREATE TABLE statements.
-- All columns are nullable — existing rows unaffected.
-- ============================================================

-- ── groups ────────────────────────────────────────────────────────────────────
ALTER TABLE groups
    ADD COLUMN IF NOT EXISTS group_id   UUID REFERENCES groups(id),
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── contribution_cycles ───────────────────────────────────────────────────────
ALTER TABLE contribution_cycles
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── contribution_entries ──────────────────────────────────────────────────────
ALTER TABLE contribution_entries
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── members ───────────────────────────────────────────────────────────────────
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS updated_by  UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS exited_on   DATE,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- ── loan_accounts ─────────────────────────────────────────────────────────────
ALTER TABLE loan_accounts
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── loan_products ─────────────────────────────────────────────────────────────
ALTER TABLE loan_products
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id);

-- ── repayment_installments ────────────────────────────────────────────────────
ALTER TABLE repayment_installments
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS version    BIGINT NOT NULL DEFAULT 0;