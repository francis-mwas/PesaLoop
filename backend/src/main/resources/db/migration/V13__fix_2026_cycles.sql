-- ============================================================
-- V13__fix_2026_cycles.sql
-- Removes the incorrectly seeded April 2026 cycle and ensures
-- March 2026 is correctly set to OPEN status.
-- Today is March 2026 — April cycle does not exist yet.
-- ============================================================

-- 1. Delete April 2026 contribution entries (FK first)
DELETE FROM contribution_entries
WHERE cycle_id IN (
    SELECT id FROM contribution_cycles
    WHERE group_id = '22222222-0000-0000-0000-000000000001'
      AND financial_year = 2026
      AND cycle_number = 4
);

-- 2. Delete the April 2026 cycle
DELETE FROM contribution_cycles
WHERE group_id = '22222222-0000-0000-0000-000000000001'
  AND financial_year = 2026
  AND cycle_number = 4;

-- 3. Ensure March 2026 is OPEN (not CLOSED — due date Mar 31 hasn't passed)
UPDATE contribution_cycles
SET status                 = 'OPEN',
    total_collected_amount = COALESCE(
            (SELECT SUM(ce.paid_amount)
             FROM contribution_entries ce
             WHERE ce.cycle_id = contribution_cycles.id),
            0),
    total_arrears_amount   = 0,
    updated_at             = NOW()
WHERE group_id    = '22222222-0000-0000-0000-000000000001'
  AND financial_year = 2026
  AND cycle_number   = 3;

-- 4. Fix savings balances — recompute from actual contribution entries
--    (removes any stale April paid_amount that inflated the balance)
UPDATE members m
SET savings_balance = (
    SELECT COALESCE(SUM(ce.paid_amount), 0)
    FROM contribution_entries ce
    WHERE ce.member_id = m.id
      AND ce.group_id  = m.group_id
),
    updated_at = NOW()
WHERE group_id = '22222222-0000-0000-0000-000000000001';