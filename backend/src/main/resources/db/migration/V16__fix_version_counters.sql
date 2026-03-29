-- ============================================================
-- V16__fix_version_counters.sql
-- V13 and V14 ran UPDATE statements without incrementing @Version.
-- Hibernate's optimistic locking now fails because the JPA entity
-- reads version=0 from original seed but the DB has stale version.
-- This migration resets all affected entities to a consistent version.
-- ============================================================

-- Fix loan_accounts version - all were updated by V14
UPDATE loan_accounts
SET version = 2
WHERE group_id = '22222222-0000-0000-0000-000000000001'
  AND version = 0;

-- Fix contribution_cycles version - updated by V12/V13
UPDATE contribution_cycles
SET version = 2
WHERE group_id = '22222222-0000-0000-0000-000000000001'
  AND version = 0;

-- Fix contribution_entries version - potentially updated by V13
UPDATE contribution_entries
SET version = 2
WHERE group_id = '22222222-0000-0000-0000-000000000001'
  AND version = 0;

-- Fix members version - updated by V13
UPDATE members
SET version = 2
WHERE group_id = '22222222-0000-0000-0000-000000000001'
  AND version = 0;

-- Verify no zero-version rows remain for seeded group
DO $$
DECLARE v_count INT;
BEGIN
SELECT COUNT(*) INTO v_count
FROM loan_accounts
WHERE group_id = '22222222-0000-0000-0000-000000000001'
  AND version = 0;
RAISE NOTICE 'Remaining zero-version loan_accounts: %', v_count;
END $$;