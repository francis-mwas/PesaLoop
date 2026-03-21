package com.pesaloop.payment.application.usecase;

import com.pesaloop.payment.application.port.in.ProcessBillingPaymentPort;

import com.pesaloop.payment.application.port.out.PaymentRecordRepository;
import com.pesaloop.payment.application.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Handles payments TO PesaLoop's own paybill — group admins paying
 * their monthly subscription fee.
 *
 * Admin pays:
 *   Business No: {PESALOOP_PAYBILL}     ← PesaLoop's own M-Pesa paybill
 *   Account No:  wanjiku-table-banking  ← group slug as account reference
 *   Amount:      500
 *
 * All persistence via domain ports — no JDBC here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PesaLoopBillingUseCase implements ProcessBillingPaymentPort {

    private final SubscriptionRepository subscriptions;
    private final PaymentRecordRepository paymentRecords;
    private final SubscriptionLifecycleService lifecycleService;

    @Value("${pesaloop.billing.paybill:123456}")
    private String pesaLoopPaybill;

    public boolean isPesaLoopPaybill(String shortCode) {
        return pesaLoopPaybill.equals(shortCode);
    }

    @Transactional
    public void process(String transactionId, BigDecimal amount,
                        String phone, String accountRef, String shortCode) {

        // Idempotency — check if this M-Pesa ref already paid an invoice
        if (paymentRecords.alreadyProcessed(transactionId)) {
            log.info("PesaLoop billing already processed: transId={}", transactionId);
            return;
        }

        // Sanitise account reference — member typed this on their phone
        String cleanRef = accountRef != null
                ? accountRef.replaceAll("[^a-zA-Z0-9\\-_ ]", "").trim().toLowerCase()
                : "";

        // Resolve group by slug first, then name partial match
        UUID groupId = subscriptions.findGroupIdBySlug(cleanRef)
                .or(() -> subscriptions.findGroupIdByName(cleanRef))
                .orElse(null);

        if (groupId == null) {
            log.warn("PesaLoop billing: cannot resolve group for accountRef={} transId={}", accountRef, transactionId);
            paymentRecords.saveUnmatchedPayment(transactionId, amount, phone, accountRef, shortCode);
            return;
        }

        String planCode = detectPlan(amount);
        lifecycleService.activateSubscription(groupId, planCode, transactionId, amount);

        log.info("PesaLoop subscription payment: group={} amount={} plan={} transId={}",
                groupId, amount, planCode, transactionId);
    }

    private String detectPlan(BigDecimal amount) {
        int kes = amount.intValue();
        if (kes >= 15000) return "PRO";
        if (kes >= 5000)  return "GROWTH";
        if (kes >= 1500)  return "PRO";
        if (kes >= 500)   return "GROWTH";
        return "FREE";
    }
}
