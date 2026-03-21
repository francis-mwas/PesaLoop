package com.pesaloop.shared.adapters.web;

import com.pesaloop.shared.domain.SubscriptionInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes HTTP 402 and 410 responses when the subscription guard blocks a request.
 * Extracted from SubscriptionAccessGuard to keep that class focused on routing logic only.
 */
@Component
public class SubscriptionResponseWriter {

    @Value("${pesaloop.billing.paybill:123456}")
    private String pesaLoopPaybill;

    public void writeBlocked(HttpServletResponse response,
                             SubscriptionInfo sub,
                             String reason) throws Exception {
        response.setStatus(402);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String planPrice          = planPriceKes(sub.planCode());
        String planPriceFormatted = formatKes(planPrice);

        String message = "SUSPENDED".equals(reason)
                ? "Your PesaLoop subscription is paused. Pay KES " + planPriceFormatted
                  + " to reactivate instantly and continue managing your group."
                : "Your PesaLoop subscription was cancelled. "
                  + "Your data is still readable during the export window. "
                  + "Pay KES " + planPriceFormatted + " to reactivate.";

        writeJson(response, """
                {
                  "success": false,
                  "errorCode": "SERVICE_%s",
                  "message": "%s",
                  "paymentDetails": {
                    "paybill": "%s",
                    "accountReference": "%s",
                    "amountKes": %s,
                    "instructions": "M-Pesa → Lipa na M-Pesa → Pay Bill → Business No: %s → Account No: %s"
                  }
                }
                """.formatted(
                reason,
                message.replace("\"", "'"),
                pesaLoopPaybill, sub.groupSlug(),
                planPrice,
                pesaLoopPaybill, sub.groupSlug()
        ));
    }

    public void writeGone(HttpServletResponse response,
                          SubscriptionInfo sub) throws Exception {
        response.setStatus(410);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        writeJson(response, """
                {
                  "success": false,
                  "errorCode": "SERVICE_CANCELLED",
                  "message": "This group's subscription is cancelled and the data export window has expired. Contact support to discuss reactivation.",
                  "paymentDetails": {
                    "paybill": "%s",
                    "accountReference": "%s",
                    "amountKes": %s,
                    "instructions": "M-Pesa → Lipa na M-Pesa → Pay Bill → Business No: %s → Account No: %s"
                  }
                }
                """.formatted(
                pesaLoopPaybill, sub.groupSlug(), planPriceKes(sub.planCode()),
                pesaLoopPaybill, sub.groupSlug()
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeJson(HttpServletResponse response, String json) throws Exception {
        response.getWriter().write(json.strip());
    }

    /** Returns the KES amount as a plain integer string for JSON. */
    private String planPriceKes(String planCode) {
        return switch (planCode == null ? "" : planCode) {
            case "PRO"        -> "1500";
            case "ENTERPRISE" -> "0";    // custom pricing — show 0 to avoid confusion
            case "FREE"       -> "500";  // FREE trial expired → default to GROWTH price
            default           -> "500";  // GROWTH
        };
    }

    /** Formatted string for human-readable messages e.g. "1,500". */
    private String formatKes(String rawKes) {
        try {
            return String.format("%,d", Integer.parseInt(rawKes));
        } catch (NumberFormatException e) {
            return rawKes;
        }
    }
}
