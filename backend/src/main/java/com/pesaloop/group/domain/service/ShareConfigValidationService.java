package com.pesaloop.group.domain.service;

import com.pesaloop.group.domain.model.Member;
import com.pesaloop.group.domain.model.ShareConfig;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

/**
 * Domain service — validates share configuration changes at the group level.
 *
 * Share change rules belong here, not on Member or ShareConfig, because
 * the validation requires knowledge of BOTH the individual member AND
 * the group-wide policy (mid-year lock, total cap, etc.).
 */
public class ShareConfigValidationService {

    /**
     * Validates whether a share count change is permitted for a member.
     * Throws IllegalStateException with a clear message if not allowed.
     */
    public void validateShareChange(
            Member member,
            int requestedShares,
            ShareConfig config,
            List<Member> allActiveMembers) {

        // Range check
        config.validateShareCount(requestedShares);

        // Mid-year lock — many chamaas freeze shares after financial year starts
        boolean isMidYear = isMidFinancialYear(config);
        if (!config.isAllowShareChangeMidYear() && isMidYear) {
            throw new IllegalStateException(
                "This group does not allow share changes mid-year. " +
                "Changes are permitted from January 1st.");
        }

        // Group total share cap (optional — not all groups enforce this)
        if (config.getMaxTotalGroupShares() > 0) {
            int currentGroupTotal = allActiveMembers.stream()
                    .mapToInt(Member::getSharesOwned).sum();
            int delta = requestedShares - member.getSharesOwned();
            if (currentGroupTotal + delta > config.getMaxTotalGroupShares()) {
                throw new IllegalStateException(
                    "This change would exceed the group's total share limit of " +
                    config.getMaxTotalGroupShares() + ". Current total: " + currentGroupTotal);
            }
        }
    }

    /**
     * Returns true if we are past the first month of the financial year
     * (i.e. January for calendar-year groups).
     */
    private boolean isMidFinancialYear(ShareConfig config) {
        LocalDate today = LocalDate.now();
        return today.getMonth() != Month.JANUARY;
    }
}
