package com.pesaloop.contribution.application.port.in;

import com.pesaloop.contribution.application.dto.ContributionDtos.ContributionEntryResponse;
import com.pesaloop.contribution.application.dto.ContributionDtos.RecordManualPaymentRequest;
import java.util.UUID;

/** Input port — record a manual contribution payment (cash, bank transfer, etc.). */
public interface RecordContributionPort {
    ContributionEntryResponse execute(RecordManualPaymentRequest request, UUID recordedByUserId);
}
