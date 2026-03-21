-- ============================================================
-- V8__seed_test_data.sql
-- DEV / TEST ONLY — never runs in production because:
--   1. application-dev.yml sets flyway.locations to include classpath:db/seed
--   2. Production application.yml does NOT include db/seed
--   3. Additionally guarded below by checking for existing data
-- ============================================================

-- Guard: skip entirely if real data already exists
-- (In production, real groups will exist; this prevents accidental seed)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM groups
         WHERE slug NOT IN (
             'wanjiku-table-banking','nairobi-welfare-group',
             'mombasa-bodaboda-daily','suspended-test-group','fresh-trial-group'
         )
         LIMIT 1
    ) THEN
        RAISE NOTICE 'V8 seed skipped — production data detected';
        RETURN;
END IF;
END $$;


-- ── 1. Users (real Kenyan names, fake phones/IDs) ────────────────────────────

INSERT INTO users (id, phone_number, email, full_name, national_id,
                   password_hash, status, account_type, kyc_level,
                   phone_verified, email_verified, created_at, updated_at, version)
VALUES
-- Password for all: "test1234" (BCrypt hash)
('11111111-0000-0000-0000-000000000001', '254712000001', 'alice@test.co.ke',
 'Alice Wanjiru Watiri',   '12345671',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,TRUE, NOW(),NOW(),0),

('11111111-0000-0000-0000-000000000002', '254722000002', 'brian@test.co.ke',
 'Brian Mwangi Kahara',    '12345672',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,FALSE, NOW(),NOW(),0),

('11111111-0000-0000-0000-000000000003', '254733000003', NULL,
 'Catherine Njeri Kamau',  '12345673',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,FALSE, NOW(),NOW(),0),

('11111111-0000-0000-0000-000000000004', '254700000004', NULL,
 'Daniel Kiprotich Karoki','12345674',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,FALSE, NOW(),NOW(),0),

('11111111-0000-0000-0000-000000000005', '254711000005', NULL,
 'Esther Akinyi Odhiambo', '12345675',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,FALSE, NOW(),NOW(),0),

('11111111-0000-0000-0000-000000000006', '254799000006', NULL,
 'Francis Otieno Ouma',    '12345676',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,FALSE, NOW(),NOW(),0),

('11111111-0000-0000-0000-000000000007', '254788000007', NULL,
 'Grace Wambui Njenga',    '12345677',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,FALSE, NOW(),NOW(),0),

('11111111-0000-0000-0000-000000000008', '254777000008', NULL,
 'Hassan Abdi Mohamed',    '12345678',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','SELF_REGISTERED','PHONE_VERIFIED',TRUE,FALSE, NOW(),NOW(),0),

-- Admin-created stub (invite not yet accepted)
('11111111-0000-0000-0000-000000000099', '254700000099', NULL,
 'Pending Invite Member', NULL,
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMqJqhNKsBsGf8KJ.KJWyH5e9K',
 'ACTIVE','ADMIN_CREATED','NONE',
 FALSE,FALSE, NOW(),NOW(),0)
    ON CONFLICT (phone_number) DO NOTHING;

-- ── 2. Groups ─────────────────────────────────────────────────────────────────

INSERT INTO groups (id, name, slug, group_types, status, currency_code,
                    share_price_amount, share_price_currency, minimum_shares, maximum_shares,
                    contribution_frequency,
                    mpesa_shortcode, mpesa_shortcode_type,
                    created_by, created_at, updated_at, version)
VALUES
-- Scenario 1: Active table banking group with loans
('22222222-0000-0000-0000-000000000001',
 'Wanjiku Table Banking', 'wanjiku-table-banking',
 '{TABLE_BANKING}', 'ACTIVE', 'KES',
 3000.00, 'KES', 1, 25,
 'MONTHLY',
 '522533', 'PAYBILL',
 '11111111-0000-0000-0000-000000000001', NOW()-INTERVAL '8 months', NOW(),0),

-- Scenario 2: MGR/welfare group, no loans
('22222222-0000-0000-0000-000000000002',
 'Nairobi Welfare Group', 'nairobi-welfare-group',
 '{WELFARE}', 'ACTIVE', 'KES',
 5000.00, 'KES', 1, 1,
 'MONTHLY',
 NULL, NULL,
 '11111111-0000-0000-0000-000000000005', NOW()-INTERVAL '3 months', NOW(),0),

-- Scenario 3: Daily contribution SACCO
('22222222-0000-0000-0000-000000000003',
 'Mombasa Bodaboda Daily', 'mombasa-bodaboda-daily',
 '{MERRY_GO_ROUND}', 'ACTIVE', 'KES',
 20.00, 'KES', 1, 1,
 'DAILY',
 '891234', 'TILL',
 '11111111-0000-0000-0000-000000000007', NOW()-INTERVAL '1 month', NOW(),0),

-- Scenario 4: Suspended group (for testing 402)
('22222222-0000-0000-0000-000000000004',
 'Suspended Test Group', 'suspended-test-group',
 '{TABLE_BANKING}', 'ACTIVE', 'KES',
 2000.00, 'KES', 1, 10,
 'MONTHLY',
 NULL, NULL,
 '11111111-0000-0000-0000-000000000008', NOW()-INTERVAL '6 months', NOW(),0),

-- Scenario 5: Fresh trial group
('22222222-0000-0000-0000-000000000005',
 'Fresh Trial Group', 'fresh-trial-group',
 '{INVESTMENT}', 'ACTIVE', 'KES',
 10000.00, 'KES', 1, 5,
 'MONTHLY',
 NULL, NULL,
 '11111111-0000-0000-0000-000000000006', NOW()-INTERVAL '2 days', NOW(),0)
    ON CONFLICT (slug) DO NOTHING;

-- ── 3. Subscriptions ──────────────────────────────────────────────────────────

INSERT INTO group_subscriptions (group_id, plan_code, status,
                                 trial_ends_at, current_period_start, current_period_end, next_invoice_at,
                                 created_at, updated_at)
VALUES
    ('22222222-0000-0000-0000-000000000001', 'GROWTH', 'ACTIVE',
     NOW()-INTERVAL '7 months', CURRENT_DATE-INTERVAL '1 month', CURRENT_DATE+INTERVAL '1 month',
     NOW()+INTERVAL '1 month', NOW(),NOW()),
    ('22222222-0000-0000-0000-000000000002', 'GROWTH', 'ACTIVE',
     NOW()-INTERVAL '2 months', CURRENT_DATE-INTERVAL '1 month', CURRENT_DATE+INTERVAL '1 month',
     NOW()+INTERVAL '1 month', NOW(),NOW()),
    ('22222222-0000-0000-0000-000000000003', 'GROWTH', 'TRIAL',
     NOW()+INTERVAL '15 days', NULL, NULL, NULL, NOW(),NOW()),
    ('22222222-0000-0000-0000-000000000004', 'GROWTH', 'SUSPENDED',
     NOW()-INTERVAL '5 months', CURRENT_DATE-INTERVAL '2 months', CURRENT_DATE-INTERVAL '1 month',
     NOW()-INTERVAL '1 month', NOW(),NOW()),
    ('22222222-0000-0000-0000-000000000005', 'FREE', 'TRIAL',
     NOW()+INTERVAL '28 days', NULL, NULL, NULL, NOW(),NOW())
    ON CONFLICT (group_id) DO NOTHING;

-- ── 4. Members ────────────────────────────────────────────────────────────────

-- Group 1: Wanjiku Table Banking (5 members, varying shares from real spreadsheet)
INSERT INTO members (id, group_id, user_id, member_number, role, status,
                     shares_owned, savings_balance, arrears_balance, fines_balance,
                     phone_number, joined_on, created_by, created_at, updated_at, version)
VALUES
    ('33333333-0000-0000-0001-000000000001','22222222-0000-0000-0000-000000000001',
     '11111111-0000-0000-0000-000000000001','M-001','ADMIN','ACTIVE',
     17, 136000.00,0,0,'254712000001','2024-01-15',
     '11111111-0000-0000-0000-000000000001',NOW(),NOW(),0),

    ('33333333-0000-0000-0001-000000000002','22222222-0000-0000-0000-000000000001',
     '11111111-0000-0000-0000-000000000002','M-002','TREASURER','ACTIVE',
     25, 200000.00,0,0,'254722000002','2024-01-15',
     '11111111-0000-0000-0000-000000000001',NOW(),NOW(),0),

    ('33333333-0000-0000-0001-000000000003','22222222-0000-0000-0000-000000000001',
     '11111111-0000-0000-0000-000000000003','M-003','MEMBER','ACTIVE',
     13, 104000.00,0,0,'254733000003','2024-01-15',
     '11111111-0000-0000-0000-000000000001',NOW(),NOW(),0),

    ('33333333-0000-0000-0001-000000000004','22222222-0000-0000-0000-000000000001',
     '11111111-0000-0000-0000-000000000004','M-004','MEMBER','ACTIVE',
     15, 120000.00,0,0,'254700000004','2024-02-01',
     '11111111-0000-0000-0000-000000000001',NOW(),NOW(),0),

    ('33333333-0000-0000-0001-000000000005','22222222-0000-0000-0000-000000000001',
     '11111111-0000-0000-0000-000000000005','M-005','MEMBER','ACTIVE',
     4, 32000.00,3000,0,'254711000005','2024-02-01',
     '11111111-0000-0000-0000-000000000001',NOW(),NOW(),0),

-- Group 2: Nairobi Welfare Group
    ('33333333-0000-0000-0002-000000000001','22222222-0000-0000-0000-000000000002',
     '11111111-0000-0000-0000-000000000005','M-001','ADMIN','ACTIVE',
     1, 15000.00,0,0,'254711000005','2024-09-01',
     '11111111-0000-0000-0000-000000000005',NOW(),NOW(),0),

    ('33333333-0000-0000-0002-000000000002','22222222-0000-0000-0000-000000000002',
     '11111111-0000-0000-0000-000000000006','M-002','TREASURER','ACTIVE',
     1, 15000.00,0,0,'254799000006','2024-09-01',
     '11111111-0000-0000-0000-000000000005',NOW(),NOW(),0),

    ('33333333-0000-0000-0002-000000000003','22222222-0000-0000-0000-000000000002',
     '11111111-0000-0000-0000-000000000007','M-003','MEMBER','ACTIVE',
     1, 10000.00,5000,0,'254788000007','2024-09-01',
     '11111111-0000-0000-0000-000000000005',NOW(),NOW(),0),

-- Group 3: Mombasa Daily SACCO
    ('33333333-0000-0000-0003-000000000001','22222222-0000-0000-0000-000000000003',
     '11111111-0000-0000-0000-000000000007','M-001','ADMIN','ACTIVE',
     1, 600.00,0,0,'254788000007','2024-12-01',
     '11111111-0000-0000-0000-000000000007',NOW(),NOW(),0),

    ('33333333-0000-0000-0003-000000000002','22222222-0000-0000-0000-000000000003',
     '11111111-0000-0000-0000-000000000008','M-002','MEMBER','ACTIVE',
     1, 540.00,60,0,'254777000008','2024-12-01',
     '11111111-0000-0000-0000-000000000007',NOW(),NOW(),0),

-- Group 4: Suspended group
    ('33333333-0000-0000-0004-000000000001','22222222-0000-0000-0000-000000000004',
     '11111111-0000-0000-0000-000000000008','M-001','ADMIN','ACTIVE',
     1, 20000.00,0,0,'254777000008','2024-06-01',
     '11111111-0000-0000-0000-000000000008',NOW(),NOW(),0)
    ON CONFLICT DO NOTHING;

-- member_number_counters
INSERT INTO member_number_counters (group_id, last_number)
VALUES
    ('22222222-0000-0000-0000-000000000001', 5),
    ('22222222-0000-0000-0000-000000000002', 3),
    ('22222222-0000-0000-0000-000000000003', 2),
    ('22222222-0000-0000-0000-000000000004', 1),
    ('22222222-0000-0000-0000-000000000005', 0)
    ON CONFLICT (group_id) DO UPDATE SET last_number = EXCLUDED.last_number;

-- ── 5. Loan products ──────────────────────────────────────────────────────────

INSERT INTO loan_products (id, group_id, name, description, active,
                           interest_type, accrual_frequency, interest_rate,
                           minimum_amount, maximum_amount,
                           max_multiple_of_savings, max_repayment_periods, repayment_frequency,
                           minimum_membership_months, minimum_shares_owned,
                           requires_guarantor, requires_zero_arrears, max_concurrent_loans,
                           late_repayment_penalty_rate, penalty_grace_period_days,
                           created_by, created_at, updated_at, version)
VALUES
-- Standard table banking loan (10% flat rate, 3-month max)
('44444444-0000-0000-0001-000000000001',
 '22222222-0000-0000-0000-000000000001',
 'Table Banking Loan', '10% flat rate, up to 3x savings, 3 months max',
 TRUE, 'FLAT', 'FLAT_RATE', 0.10,
 1000.00, 500000.00,
 3.0, 3, 'MONTHLY',
 3, 1, FALSE, TRUE, 1,
 0.05, 3,
 '11111111-0000-0000-0000-000000000001', NOW(),NOW(),0),

-- Emergency loan (5% flat, 1-month turnaround)
('44444444-0000-0000-0001-000000000002',
 '22222222-0000-0000-0000-000000000001',
 'Emergency Loan', '5% flat rate, up to 1x savings, 1 month',
 TRUE, 'FLAT', 'FLAT_RATE', 0.05,
 500.00, 50000.00,
 1.0, 1, 'MONTHLY',
 1, 1, FALSE, FALSE, 1,
 0.10, 0,
 '11111111-0000-0000-0000-000000000001', NOW(),NOW(),0)
    ON CONFLICT DO NOTHING;

-- ── 6. Contribution cycles ────────────────────────────────────────────────────

INSERT INTO contribution_cycles (id, group_id, cycle_number, financial_year,
                                 due_date, grace_period_end, status,
                                 total_expected_amount, total_collected_amount, total_arrears_amount, total_fines_amount,
                                 currency_code, created_at, updated_at, version)
VALUES
-- Cycle 4 (current open cycle for Group 1)
('55555555-0000-0000-0001-000000000004',
 '22222222-0000-0000-0000-000000000001',
 4, 2025, CURRENT_DATE+INTERVAL '10 days', CURRENT_DATE+INTERVAL '13 days',
 'OPEN',
 222000.00, 171000.00, 0, 0,
 'KES', NOW(),NOW(),0),

-- Cycle 3 (closed)
('55555555-0000-0000-0001-000000000003',
 '22222222-0000-0000-0000-000000000001',
 3, 2025, CURRENT_DATE-INTERVAL '20 days', CURRENT_DATE-INTERVAL '17 days',
 'CLOSED',
 222000.00, 222000.00, 0, 0,
 'KES', NOW(),NOW(),0),

-- Group 2 current cycle
('55555555-0000-0000-0002-000000000001',
 '22222222-0000-0000-0000-000000000002',
 1, 2025, CURRENT_DATE+INTERVAL '5 days', CURRENT_DATE+INTERVAL '8 days',
 'OPEN',
 15000.00, 10000.00, 0, 0,
 'KES', NOW(),NOW(),0)
    ON CONFLICT DO NOTHING;

-- ── 7. Contribution entries for current cycle (Group 1) ───────────────────────

INSERT INTO contribution_entries (id, group_id, cycle_id, member_id,
                                  expected_amount, paid_amount, arrears_carried_forward, currency_code,
                                  status, created_at, updated_at, version)
VALUES
-- Alice (M-001): 17 shares × 3000 = 51000 — PAID
('66666666-0001-0004-0000-000000000001',
 '22222222-0000-0000-0000-000000000001',
 '55555555-0000-0000-0001-000000000004',
 '33333333-0000-0000-0001-000000000001',
 51000.00, 51000.00, 0, 'KES', 'PAID',
 NOW()-INTERVAL '3 days', NOW(),0),

-- Brian (M-002): 25 shares × 3000 = 75000 — PAID
('66666666-0001-0004-0000-000000000002',
 '22222222-0000-0000-0000-000000000001',
 '55555555-0000-0000-0001-000000000004',
 '33333333-0000-0000-0001-000000000002',
 75000.00, 75000.00, 0, 'KES', 'PAID',
 NOW()-INTERVAL '5 days', NOW(),0),

-- Catherine (M-003): 13 shares × 3000 = 39000 — PARTIAL (paid 27000)
('66666666-0001-0004-0000-000000000003',
 '22222222-0000-0000-0000-000000000001',
 '55555555-0000-0000-0001-000000000004',
 '33333333-0000-0000-0001-000000000003',
 39000.00, 27000.00, 0, 'KES', 'PARTIAL',
 NOW()-INTERVAL '1 day', NOW(),0),

-- Daniel (M-004): 15 shares × 3000 = 45000 — PENDING
('66666666-0001-0004-0000-000000000004',
 '22222222-0000-0000-0000-000000000001',
 '55555555-0000-0000-0001-000000000004',
 '33333333-0000-0000-0001-000000000004',
 45000.00, 18000.00, 0, 'KES', 'PARTIAL',
 NOW()-INTERVAL '2 days', NOW(),0),

-- Esther (M-005): 4 shares × 3000 = 12000 — PENDING
('66666666-0001-0004-0000-000000000005',
 '22222222-0000-0000-0000-000000000001',
 '55555555-0000-0000-0001-000000000004',
 '33333333-0000-0000-0001-000000000005',
 12000.00, 0, 0, 'KES', 'PENDING',
 NOW(), NOW(),0)
    ON CONFLICT DO NOTHING;

-- ── 8. Active loan — Alice has KES 100,000 loan (ACTIVE, approved) ────────────

INSERT INTO loan_accounts (id, group_id, member_id, product_id,
                           loan_reference, status,
                           principal_amount, total_interest_charged, currency_code,
                           principal_balance, accrued_interest, penalty_balance,
                           total_principal_repaid, total_interest_repaid,
                           disbursement_date, due_date,
                           disbursement_mpesa_ref,
                           application_note,
                           created_by, created_at, updated_at, version)
VALUES
-- Alice: KES 100,000 loan, 10% flat = 10,000 interest
-- Disbursed 2 months ago, 1 instalment paid
('77777777-0000-0001-0000-000000000001',
 '22222222-0000-0000-0000-000000000001',
 '33333333-0000-0000-0001-000000000001',
 '44444444-0000-0000-0001-000000000001',
 'LN-2025-0001', 'ACTIVE',
 100000.00, 10000.00, 'KES',
 66666.67, 6666.67, 0.00,
 33333.33, 3333.33,
 CURRENT_DATE-INTERVAL '2 months', CURRENT_DATE+INTERVAL '1 month',
 'SHK7N2A1B3',
 'Business expansion — buying stock',
 '11111111-0000-0000-0000-000000000001', NOW()-INTERVAL '2 months', NOW(),0),

-- Brian: KES 350,000 loan — PENDING_DISBURSEMENT (disbursement instruction outstanding)
('77777777-0000-0001-0000-000000000002',
 '22222222-0000-0000-0000-000000000001',
 '33333333-0000-0000-0001-000000000002',
 '44444444-0000-0000-0001-000000000001',
 'LN-2025-0002', 'PENDING_DISBURSEMENT',
 350000.00, 35000.00, 'KES',
 0.00, 0.00, 0.00,
 0.00, 0.00,
 NULL, CURRENT_DATE+INTERVAL '3 months',
 NULL, 'Motor vehicle purchase',
 '11111111-0000-0000-0000-000000000001', NOW()-INTERVAL '1 day', NOW(),0)
    ON CONFLICT DO NOTHING;

-- loan_reference_counters
INSERT INTO loan_reference_counters (group_id, year, last_number)
VALUES ('22222222-0000-0000-0000-000000000001', 2025, 2)
    ON CONFLICT (group_id, year) DO UPDATE SET last_number = EXCLUDED.last_number;

-- ── 9. Disbursement instruction for Brian's loan ──────────────────────────────

INSERT INTO disbursement_instructions (id, group_id, instruction_type,
                                       loan_id, source_reference,
                                       recipient_member_id, recipient_name, recipient_phone,
                                       suggested_account_reference, amount_kes, status,
                                       expires_at, issued_by, issued_at, created_at, updated_at)
VALUES
    ('88888888-0000-0001-0000-000000000002',
     '22222222-0000-0000-0000-000000000001',
     'LOAN_DISBURSEMENT',
     '77777777-0000-0001-0000-000000000002',
     'LN-2025-0002',
     '33333333-0000-0000-0001-000000000002',
     'Brian Mwangi Kahara',
     '254722000002',
     'LN-2025-0002',
     350000.00,
     'PENDING',
     NOW()+INTERVAL '2 days',
     '11111111-0000-0000-0000-000000000001',
     NOW()-INTERVAL '1 day',
     NOW(), NOW())
    ON CONFLICT DO NOTHING;

-- ── 10. Payment accounts ──────────────────────────────────────────────────────

INSERT INTO group_payment_accounts (id, group_id, account_type, provider,
                                    account_number, account_name, is_collection, is_disbursement, is_primary,
                                    c2b_registered, status, display_label,
                                    created_by, created_at, updated_at, version)
VALUES
    ('99999999-0000-0001-0000-000000000001',
     '22222222-0000-0000-0000-000000000001',
     'MPESA_PAYBILL', 'SAFARICOM_MPESA',
     '522533', 'Wanjiku Table Banking',
     TRUE, FALSE, TRUE,
     TRUE, 'ACTIVE', 'M-Pesa Paybill 522533',
     '11111111-0000-0000-0000-000000000001', NOW(),NOW(),0),

    ('99999999-0000-0003-0000-000000000001',
     '22222222-0000-0000-0000-000000000003',
     'MPESA_TILL', 'SAFARICOM_MPESA',
     '891234', 'Mombasa Bodaboda Daily',
     TRUE, FALSE, TRUE,
     FALSE, 'ACTIVE', 'M-Pesa Till 891234',
     '11111111-0000-0000-0000-000000000007', NOW(),NOW(),0)
    ON CONFLICT DO NOTHING;