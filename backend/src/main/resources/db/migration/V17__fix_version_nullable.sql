-- ============================================================
-- V17__fix_version_nullable.sql
-- BaseEntity now uses Long version (nullable) instead of Long version = 0L
-- so Spring Data JPA can correctly distinguish new vs existing entities.
-- NULL version → brand new (PERSIST), non-null → existing (MERGE).
-- We must allow NULL in the DB column and ensure all existing rows are non-null.
-- ============================================================

-- Make version nullable in all entity tables
ALTER TABLE groups               ALTER COLUMN version DROP NOT NULL;
ALTER TABLE members              ALTER COLUMN version DROP NOT NULL;
ALTER TABLE loan_accounts        ALTER COLUMN version DROP NOT NULL;
ALTER TABLE contribution_cycles  ALTER COLUMN version DROP NOT NULL;
ALTER TABLE contribution_entries ALTER COLUMN version DROP NOT NULL;

-- Ensure all existing rows have a non-null version (already done by V16,
-- but be safe in case V16 wasn't applied or didn't cover everything)
UPDATE groups               SET version = 1 WHERE version IS NULL;
UPDATE members              SET version = 1 WHERE version IS NULL;
UPDATE loan_accounts        SET version = 1 WHERE version IS NULL;
UPDATE contribution_cycles  SET version = 1 WHERE version IS NULL;
UPDATE contribution_entries SET version = 1 WHERE version IS NULL;