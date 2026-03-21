package com.pesaloop.payment.adapters.web;

import com.pesaloop.payment.application.port.out.MpesaGateway;
import com.pesaloop.payment.application.port.out.PaymentAccountRepository;
import com.pesaloop.payment.application.port.out.PaymentAccountRepository.*;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Primary adapter — group payment accounts and subscription view. No SQL. */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment-accounts")
@RequiredArgsConstructor
public class PaymentAccountController {

    private final PaymentAccountRepository paymentAccountRepository;
    private final MpesaGateway mpesaGateway;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR')")
    public ResponseEntity<ApiResponse<List<PaymentAccountRow>>> listAccounts() {
        return ResponseEntity.ok(ApiResponse.success(
                paymentAccountRepository.findByGroupId(TenantContext.getGroupId())));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<UUID>> addAccount(
            @Valid @RequestBody AddPaymentAccountRequest req,
            @AuthenticationPrincipal String userId) {

        UUID groupId = TenantContext.getGroupId();
        UUID accountId = paymentAccountRepository.createAccount(
                groupId, UUID.fromString(userId),
                req.accountType(), req.provider(), req.accountNumber(), req.accountName(),
                req.bankBranch(), req.bankSwiftCode(), req.displayLabel(),
                Boolean.TRUE.equals(req.isCollection()), Boolean.TRUE.equals(req.isDisbursement()));

        if ("PAYBILL".equalsIgnoreCase(req.accountType()) && Boolean.TRUE.equals(req.autoRegisterC2b())) {
            try {
                mpesaGateway.registerC2bUrls(req.accountNumber());
                paymentAccountRepository.markC2bRegistered(accountId);
            } catch (Exception e) {
                log.warn("C2B auto-registration failed for account {}: {}", accountId, e.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accountId, "Payment account added."));
    }

    @PutMapping("/{accountId}/set-primary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setPrimary(
            @PathVariable UUID accountId,
            @RequestBody SetPrimaryRequest req) {
        UUID groupId = TenantContext.getGroupId();
        paymentAccountRepository.setPrimary(accountId, groupId,
                Boolean.TRUE.equals(req.isCollection()), Boolean.TRUE.equals(req.isDisbursement()));
        return ResponseEntity.ok(ApiResponse.success(null, "Primary account updated."));
    }

    @PostMapping("/{accountId}/register-c2b")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> registerC2b(@PathVariable UUID accountId) {
        String shortcode = paymentAccountRepository.findShortcodeById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        mpesaGateway.registerC2bUrls(shortcode);
        paymentAccountRepository.markC2bRegistered(accountId);
        return ResponseEntity.ok(ApiResponse.success(null,
                "C2B URLs registered with Safaricom for shortcode " + shortcode));
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<SubscriptionRow>> getSubscription() {
        return ResponseEntity.ok(ApiResponse.success(
                paymentAccountRepository.findSubscription(TenantContext.getGroupId())));
    }

    public record AddPaymentAccountRequest(
            @NotBlank String accountType, @NotBlank String provider,
            @NotBlank String accountNumber, String accountName,
            String bankBranch, String bankSwiftCode, String displayLabel,
            Boolean isCollection, Boolean isDisbursement, Boolean autoRegisterC2b) {}

    public record SetPrimaryRequest(Boolean isCollection, Boolean isDisbursement) {}
}
