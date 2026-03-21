package com.pesaloop.contribution.application.port.in;

import com.pesaloop.contribution.application.dto.ContributionDtos.CycleSummaryResponse;
import java.util.UUID;

/** Input port — get the summary of a contribution cycle. */
public interface GetCycleSummaryPort {
    CycleSummaryResponse execute(UUID cycleId);
}
