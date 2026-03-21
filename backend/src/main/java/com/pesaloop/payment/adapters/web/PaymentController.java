package com.pesaloop.payment.adapters.web;

import com.pesaloop.payment.application.port.in.RecordManualPaymentPort;
import com.pesaloop.payment.application.port.out.MpesaGateway;
import com.pesaloop.payment.application.port.out.PaymentQueryRepository;
import com.pesaloop.payment.application.port.out.PaymentQueryRepository.*;
import com.pesaloop.payment.application.usecase.RecordManualPaymentUseCase.ManualPaymentRequest;
import com.pesaloop.payment.application.usecase.RecordManualPaymentUseCase.ManualPaymentResponse;
import com.pesaloop.payment.application.usecase.RecordManualPaymentUseCase.PaymentTarget;
import com.pesaloop.payment.domain.model.PaymentEntryMethod;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Primary adapter — manual payment recording, paybill registration, payment history. No SQL. */
@Slf4j
@Tag(name = "Payments", description = "Paybill setup, unmatched C2B, payment history")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RecordManualPaymentPort manualPaymentUseCase;
    private final PaymentQueryRepository paymentQueryRepository;
    private final MpesaGateway mpesaGateway;

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<ManualPaymentResponse>> recordManual(
            @RequestBody ManualPaymentRequest req,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                manualPaymentUseCase.execute(req, UUID.fromString(userId))));
    }

    @PostMapping("/paybill/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> registerPaybill(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {

        UUID groupId = TenantContext.getGroupId();
        String shortcode = body.get("shortcode");
        String type = body.getOrDefault("type", "PAYBILL");

        if (shortcode == null || shortcode.isBlank())
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("shortcode is required", "MISSING_SHORTCODE"));

        paymentQueryRepository.registerPaybillShortcode(groupId, shortcode, type);

        if ("PAYBILL".equalsIgnoreCase(type)) {
            try {
                mpesaGateway.registerC2bUrls(shortcode);
            } catch (Exception e) {
                log.error("Safaricom C2B registration failed: {}", e.getMessage());
                return ResponseEntity.ok(ApiResponse.success(
                        Map.of("shortcode", shortcode, "status", "SAVED_BUT_REGISTRATION_FAILED",
                               "note", "Shortcode saved. Safaricom registration failed: " + e.getMessage()),
                        null));
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("shortcode", shortcode, "type", type, "status", "REGISTERED"),
                "Paybill registered. Members can pay using shortcode " + shortcode +
                " with their member number as account reference."));
    }

    @GetMapping("/unmatched")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<List<UnmatchedPaymentRow>>> getUnmatched() {
        return ResponseEntity.ok(ApiResponse.success(
                paymentQueryRepository.findUnmatchedByGroup(TenantContext.getGroupId())));
    }

    @PostMapping("/unmatched/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<Void>> resolveUnmatched(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {

        UUID memberId = UUID.fromString(body.get("memberId"));
        String notes  = body.getOrDefault("notes", "");

        UnmatchedPaymentRow payment = paymentQueryRepository.findUnmatchedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unmatched payment not found or already resolved"));

        manualPaymentUseCase.execute(new ManualPaymentRequest(
                memberId, PaymentTarget.CONTRIBUTION, null, null,
                payment.amount(), PaymentEntryMethod.C2B_PAYBILL,
                payment.mpesaTransactionId(),
                "Resolved unmatched C2B. Original ref: " + payment.billRefNumber()
                + (notes.isBlank() ? "" : ". " + notes), null),
                UUID.fromString(userId));

        paymentQueryRepository.markUnmatchedResolved(id, UUID.fromString(userId), notes);
        return ResponseEntity.ok(ApiResponse.success(null, "Payment resolved and applied to member"));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','MEMBER','AUDITOR')")
    public ResponseEntity<ApiResponse<List<PaymentHistoryRow>>> getHistory(
            @RequestParam(required = false) UUID memberId) {

        UUID groupId = TenantContext.getGroupId();
        if ("MEMBER".equals(TenantContext.getRole())) {
            memberId = TenantContext.getUserId();
        }
        return ResponseEntity.ok(ApiResponse.success(
                paymentQueryRepository.findPaymentHistory(groupId, memberId)));
    }
}
