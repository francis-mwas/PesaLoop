-- ============================================================
-- V15__fix_payment_method_enum.sql
-- Update legacy payment_method values from short-form ('MPESA')
-- to the full enum values that match ContributionEntry.PaymentMethod
-- ============================================================

-- Fix contribution payment records
UPDATE payment_records
SET payment_method = 'MPESA_PAYBILL'
WHERE payment_method = 'MPESA'
  AND payment_type = 'CONTRIBUTION';

-- Fix loan repayment records
UPDATE payment_records
SET payment_method = 'MPESA_PAYBILL'
WHERE payment_method = 'MPESA'
  AND payment_type = 'LOAN_REPAYMENT';

-- Fix BANK → BANK_TRANSFER
UPDATE payment_records
SET payment_method = 'BANK_TRANSFER'
WHERE payment_method = 'BANK';

-- Verify
DO $$
DECLARE
bad_count INT;
BEGIN
SELECT COUNT(*) INTO bad_count
FROM payment_records
WHERE payment_method NOT IN ('MPESA_STK_PUSH','MPESA_PAYBILL','MPESA_TILL',
                             'CASH','BANK_TRANSFER','INTERNAL_TRANSFER');
IF bad_count > 0 THEN
    RAISE WARNING 'Still % payment records with unrecognized payment_method', bad_count;
ELSE
    RAISE NOTICE 'All payment_method values are valid';
END IF;
END $$;