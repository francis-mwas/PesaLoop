package com.pesaloop.payment.adapters.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.pesaloop.payment.application.port.in.ProcessC2bPaymentPort;
import com.pesaloop.payment.application.port.in.ProcessBillingPaymentPort;

import java.math.BigDecimal;

/**
 * Receives all inbound callbacks from Safaricom M-Pesa Daraja.
 *
 * Three webhook endpoints:
 *
 *   /stk/callback      — member paid via STK Push (system-initiated)
 *   /c2b/confirmation  — member paid directly via paybill on their phone
 *   /b2c/result        — disbursement / payout result
 *
 * All endpoints MUST return {"ResultCode":"0","ResultDesc":"Accepted"}.
 * Any other response causes Safaricom to retry delivery repeatedly.
 * We absorb all internal errors and always respond 200 OK.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/mpesa")
@RequiredArgsConstructor
public class MpesaWebhookController {

    private final MpesaWebhookService    stkWebhookService;
    private final ProcessC2bPaymentPort    c2bProcessor;
    private final ProcessBillingPaymentPort billingProcessor;

    // ── STK Push callback ─────────────────────────────────────────────────────

    @PostMapping("/stk/callback")
    public ResponseEntity<MpesaAck> handleStkCallback(
            @RequestBody StkCallbackRequest request) {
        try {
            StkCallbackRequest.StkCallback cb = request.body().stkCallback();
            log.info("STK callback: merchantId={} resultCode={}",
                    cb.merchantRequestId(), cb.resultCode());

            if ("0".equals(String.valueOf(cb.resultCode()))) {
                stkWebhookService.processStkSuccess(
                        cb.merchantRequestId(),
                        cb.checkoutRequestId(),
                        extractAmount(cb),
                        extractMpesaRef(cb),
                        extractPhone(cb)
                );
            } else {
                stkWebhookService.processStkFailure(
                        cb.merchantRequestId(), cb.resultDesc());
            }
        } catch (Exception e) {
            log.error("Error processing STK callback", e);
        }
        return ResponseEntity.ok(MpesaAck.accepted());
    }

    // ── C2B confirmation (direct paybill payment) ─────────────────────────────

    /**
     * Fired when a member pays directly from their phone:
     *   Go to M-Pesa → Lipa na M-Pesa → Pay Bill
     *   → Business No: {group paybill}
     *   → Account No:  {member number e.g. M-017}
     *   → Amount: 51000
     *   → PIN
     *
     * The BillRefNumber field contains whatever the member typed as the account.
     * C2bPaymentUseCase resolves the member and applies the payment.
     */
    @PostMapping("/c2b/confirmation")
    public ResponseEntity<MpesaAck> handleC2bConfirmation(
            @RequestBody C2bCallbackRequest request) {
        try {
            log.info("C2B payment received: transId={} amount={} phone={} ref={} shortcode={}",
                    request.transactionId(), request.transAmount(),
                    request.msisdn(), request.billRefNumber(), request.businessShortCode());

            // Route to PesaLoop billing if this is payment to our own paybill,
            // otherwise route to group C2B processor (member paying their group)
            if (billingProcessor.isPesaLoopPaybill(request.businessShortCode())) {
                billingProcessor.process(
                        request.transactionId(),
                        new java.math.BigDecimal(request.transAmount()),
                        request.msisdn(),
                        request.billRefNumber(),
                        request.businessShortCode()
                );
            } else {
                c2bProcessor.process(
                        request.transactionId(),
                        new java.math.BigDecimal(request.transAmount()),
                        request.msisdn(),
                        request.billRefNumber(),
                        request.businessShortCode(),
                        request.firstName(),
                        request.lastName()
                );
            }
        } catch (Exception e) {
            log.error("Error processing C2B confirmation", e);
        }
        return ResponseEntity.ok(MpesaAck.accepted());
    }

    /**
     * Validation URL — called BEFORE confirmation on some paybill setups.
     * We always accept (return 0) to avoid blocking any legitimate payment.
     */
    @PostMapping("/c2b/validation")
    public ResponseEntity<MpesaAck> handleC2bValidation(
            @RequestBody C2bCallbackRequest request) {
        log.debug("C2B validation: transId={} phone={} ref={}",
                request.transactionId(), request.msisdn(), request.billRefNumber());
        return ResponseEntity.ok(MpesaAck.accepted());
    }

    // ── B2C result (disbursements and payouts) ────────────────────────────────

    @PostMapping("/b2c/result")
    public ResponseEntity<MpesaAck> handleB2cResult(
            @RequestBody B2cResultRequest request) {
        try {
            B2cResultRequest.Result result = request.result();
            log.info("B2C result: conversationId={} resultCode={}",
                    result.conversationId(), result.resultCode());

            if (result.resultCode() == 0) {
                stkWebhookService.processB2cSuccess(
                        result.conversationId(),
                        result.originatorConversationId(),
                        extractB2cAmount(result),
                        extractB2cReceiptNumber(result)
                );
            } else {
                stkWebhookService.processB2cFailure(
                        result.conversationId(), result.resultDesc());
            }
        } catch (Exception e) {
            log.error("Error processing B2C result", e);
        }
        return ResponseEntity.ok(MpesaAck.accepted());
    }

    // ── Metadata extractors ───────────────────────────────────────────────────

    private BigDecimal extractAmount(StkCallbackRequest.StkCallback cb) {
        if (cb.callbackMetadata() == null) return BigDecimal.ZERO;
        return cb.callbackMetadata().item().stream()
                .filter(i -> "Amount".equals(i.name()))
                .map(i -> new BigDecimal(String.valueOf(i.value())))
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private String extractMpesaRef(StkCallbackRequest.StkCallback cb) {
        if (cb.callbackMetadata() == null) return null;
        return cb.callbackMetadata().item().stream()
                .filter(i -> "MpesaReceiptNumber".equals(i.name()))
                .map(i -> String.valueOf(i.value()))
                .findFirst().orElse(null);
    }

    private String extractPhone(StkCallbackRequest.StkCallback cb) {
        if (cb.callbackMetadata() == null) return null;
        return cb.callbackMetadata().item().stream()
                .filter(i -> "PhoneNumber".equals(i.name()))
                .map(i -> String.valueOf(i.value()))
                .findFirst().orElse(null);
    }

    private BigDecimal extractB2cAmount(B2cResultRequest.Result r) {
        if (r.resultParameters() == null) return BigDecimal.ZERO;
        return r.resultParameters().resultParameter().stream()
                .filter(p -> "TransactionAmount".equals(p.key()))
                .map(p -> new BigDecimal(String.valueOf(p.value())))
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private String extractB2cReceiptNumber(B2cResultRequest.Result r) {
        if (r.resultParameters() == null) return null;
        return r.resultParameters().resultParameter().stream()
                .filter(p -> "TransactionReceipt".equals(p.key()))
                .map(p -> String.valueOf(p.value()))
                .findFirst().orElse(null);
    }

    // ── Request / response types ──────────────────────────────────────────────

    public record MpesaAck(
            @JsonProperty("ResultCode") String resultCode,
            @JsonProperty("ResultDesc") String resultDesc
    ) {
        static MpesaAck accepted() { return new MpesaAck("0", "Accepted"); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StkCallbackRequest(@JsonProperty("Body") Body body) {
        public record Body(@JsonProperty("stkCallback") StkCallback stkCallback) {}
        public record StkCallback(
                @JsonProperty("MerchantRequestID")   String merchantRequestId,
                @JsonProperty("CheckoutRequestID")   String checkoutRequestId,
                @JsonProperty("ResultCode")           int resultCode,
                @JsonProperty("ResultDesc")           String resultDesc,
                @JsonProperty("CallbackMetadata")     CallbackMetadata callbackMetadata
        ) {}
        public record CallbackMetadata(@JsonProperty("Item") java.util.List<Item> item) {}
        public record Item(@JsonProperty("Name") String name, @JsonProperty("Value") Object value) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record C2bCallbackRequest(
            @JsonProperty("TransactionType")   String transactionType,
            @JsonProperty("TransID")           String transactionId,
            @JsonProperty("TransAmount")       String transAmount,
            @JsonProperty("BusinessShortCode") String businessShortCode,
            @JsonProperty("BillRefNumber")     String billRefNumber,
            @JsonProperty("MSISDN")            String msisdn,
            @JsonProperty("FirstName")         String firstName,
            @JsonProperty("LastName")          String lastName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record B2cResultRequest(@JsonProperty("Result") Result result) {
        public record Result(
                @JsonProperty("ResultCode")                  int resultCode,
                @JsonProperty("ResultDesc")                  String resultDesc,
                @JsonProperty("OriginatorConversationID")    String originatorConversationId,
                @JsonProperty("ConversationID")              String conversationId,
                @JsonProperty("TransactionID")               String transactionId,
                @JsonProperty("ResultParameters")            ResultParameters resultParameters
        ) {}
        public record ResultParameters(
                @JsonProperty("ResultParameter") java.util.List<ResultParameter> resultParameter) {}
        public record ResultParameter(
                @JsonProperty("Key") String key,
                @JsonProperty("Value") Object value) {}
    }
}
