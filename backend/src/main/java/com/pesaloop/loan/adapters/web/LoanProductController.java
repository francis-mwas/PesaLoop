package com.pesaloop.loan.adapters.web;

import com.pesaloop.loan.application.port.out.LoanProductQueryRepository;
import com.pesaloop.loan.application.port.out.LoanProductRepository;
import com.pesaloop.loan.domain.model.LoanProduct;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import com.pesaloop.group.domain.model.InterestAccrualFrequency;
import com.pesaloop.group.domain.model.InterestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Primary adapter — loan product CRUD.
 * No SQL in this class — delegates to output port interfaces.
 */
@RestController
@RequestMapping("/api/v1/loan-products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductRepository productRepository;
    private final LoanProductQueryRepository productQueryRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','MEMBER')")
    public ResponseEntity<ApiResponse<List<LoanProductResponse>>> list() {
        List<LoanProductResponse> products = productRepository
                .findActiveByGroupId(TenantContext.getGroupId())
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','MEMBER')")
    public ResponseEntity<ApiResponse<LoanProductResponse>> get(@PathVariable UUID productId) {
        UUID groupId = TenantContext.getGroupId();
        return productRepository.findById(productId)
                .filter(p -> p.getGroupId().equals(groupId))
                .map(p -> ResponseEntity.ok(ApiResponse.success(toResponse(p))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoanProductResponse>> create(
            @Valid @RequestBody LoanProductRequest req) {

        UUID groupId = TenantContext.getGroupId();
        LoanProduct product = LoanProduct.builder()
                .id(UUID.randomUUID()).groupId(groupId)
                .name(req.name()).description(req.description()).active(true)
                .interestType(InterestType.valueOf(req.interestType()))
                .accrualFrequency(InterestAccrualFrequency.valueOf(
                        req.accrualFrequency() != null ? req.accrualFrequency()
                        : ("FLAT".equals(req.interestType()) ? "FLAT_RATE" : "MONTHLY")))
                .interestRate(req.interestRate())
                .customAccrualIntervalDays(req.customAccrualIntervalDays())
                .minimumAmount(Money.ofKes(req.minAmount() != null ? req.minAmount() : BigDecimal.valueOf(1000)))
                .maximumAmount(Money.ofKes(req.maxAmount()))
                .maxMultipleOfSavings(req.maxMultipleOfSavings())
                .maxRepaymentPeriods(req.maxRepaymentPeriods())
                .repaymentFrequency(InterestAccrualFrequency.valueOf(
                        req.repaymentFrequency() != null ? req.repaymentFrequency() : "MONTHLY"))
                .bulletRepayment(Boolean.TRUE.equals(req.bulletRepayment()))
                .minimumMembershipMonths(req.minimumMembershipMonths() != null ? req.minimumMembershipMonths() : 0)
                .minimumSharesOwned(req.minimumSharesOwned() != null ? req.minimumSharesOwned() : 1)
                .requiresGuarantor(Boolean.TRUE.equals(req.requiresGuarantor()))
                .maxGuarantors(req.maxGuarantors() != null ? req.maxGuarantors() : 1)
                .requiresZeroArrears(req.requiresZeroArrears() == null || req.requiresZeroArrears())
                .maxConcurrentLoans(req.maxConcurrentLoans() != null ? req.maxConcurrentLoans() : 1)
                .lateRepaymentPenaltyRate(req.lateRepaymentPenaltyRate() != null ? req.lateRepaymentPenaltyRate() : BigDecimal.valueOf(0.05))
                .penaltyGracePeriodDays(req.penaltyGracePeriodDays() != null ? req.penaltyGracePeriodDays() : 3)
                .build();

        LoanProduct saved = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(saved),
                        "Loan product \"" + saved.getName() + "\" created."));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoanProductResponse>> update(
            @PathVariable UUID productId,
            @RequestBody LoanProductUpdateRequest req) {

        UUID groupId = TenantContext.getGroupId();
        productRepository.findById(productId)
                .filter(p -> p.getGroupId().equals(groupId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        int activeLoans = productQueryRepository.countActiveLoansUnderProduct(productId);
        productQueryRepository.updateProduct(productId, groupId,
                req.name(), req.description(), req.interestType(), req.accrualFrequency(),
                req.interestRate(), req.minAmount(), req.maxAmount(), req.maxMultipleOfSavings(),
                req.maxRepaymentPeriods(), req.repaymentFrequency(),
                req.requiresGuarantor(), req.requiresZeroArrears(),
                req.lateRepaymentPenaltyRate(), req.active());

        LoanProduct updated = productRepository.findById(productId).orElseThrow();
        String warning = activeLoans > 0
                ? " Warning: " + activeLoans + " existing loan(s) are unaffected." : "";
        return ResponseEntity.ok(ApiResponse.success(toResponse(updated),
                "Product updated. New rate applies to future applications only." + warning));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID productId) {
        productQueryRepository.deactivateProduct(productId, TenantContext.getGroupId());
        return ResponseEntity.ok(ApiResponse.success(null, "Product deactivated. Existing loans are unaffected."));
    }

    private LoanProductResponse toResponse(LoanProduct p) {
        return new LoanProductResponse(p.getId(), p.getName(), p.getDescription(), p.isActive(),
                p.getInterestType().name(), p.getAccrualFrequency().name(), p.getInterestRate(),
                p.getMinimumAmount() != null ? p.getMinimumAmount().getAmount() : null,
                p.getMaximumAmount() != null ? p.getMaximumAmount().getAmount() : null,
                p.getMaxMultipleOfSavings(), p.getMaxRepaymentPeriods(),
                p.getRepaymentFrequency().name(), p.isBulletRepayment(),
                p.getMinimumMembershipMonths(), p.getMinimumSharesOwned(),
                p.isRequiresGuarantor(), p.getMaxGuarantors(),
                p.isRequiresZeroArrears(), p.getMaxConcurrentLoans(),
                p.getLateRepaymentPenaltyRate(), p.getPenaltyGracePeriodDays());
    }

    public record LoanProductRequest(@NotBlank String name, String description,
            @NotNull String interestType, String accrualFrequency,
            @NotNull @Positive BigDecimal interestRate, Integer customAccrualIntervalDays,
            BigDecimal minAmount, @NotNull @Positive BigDecimal maxAmount,
            BigDecimal maxMultipleOfSavings, BigDecimal maxMultipleOfSharesValue,
            @NotNull @Positive int maxRepaymentPeriods, String repaymentFrequency,
            Boolean bulletRepayment, Integer minimumMembershipMonths, Integer minimumSharesOwned,
            Boolean requiresGuarantor, Integer maxGuarantors, Boolean requiresZeroArrears,
            Integer maxConcurrentLoans, BigDecimal lateRepaymentPenaltyRate, Integer penaltyGracePeriodDays) {}

    public record LoanProductUpdateRequest(String name, String description,
            String interestType, String accrualFrequency, BigDecimal interestRate,
            BigDecimal minAmount, BigDecimal maxAmount, BigDecimal maxMultipleOfSavings,
            Integer maxRepaymentPeriods, String repaymentFrequency,
            Boolean requiresGuarantor, Boolean requiresZeroArrears,
            BigDecimal lateRepaymentPenaltyRate, Boolean active) {}

    public record LoanProductResponse(UUID id, String name, String description, boolean active,
            String interestType, String accrualFrequency, BigDecimal interestRate,
            BigDecimal minAmount, BigDecimal maxAmount, BigDecimal maxMultipleOfSavings,
            int maxRepaymentPeriods, String repaymentFrequency, boolean bulletRepayment,
            int minimumMembershipMonths, int minimumSharesOwned,
            boolean requiresGuarantor, int maxGuarantors,
            boolean requiresZeroArrears, int maxConcurrentLoans,
            BigDecimal lateRepaymentPenaltyRate, int penaltyGracePeriodDays) {}
}
