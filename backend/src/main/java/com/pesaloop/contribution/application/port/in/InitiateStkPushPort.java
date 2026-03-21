package com.pesaloop.contribution.application.port.in;

import com.pesaloop.contribution.application.dto.ContributionDtos.InitiateStkPushRequest;
import com.pesaloop.contribution.application.dto.ContributionDtos.StkPushResponse;
import java.util.UUID;

/** Input port — initiate an M-Pesa STK push for a contribution. */
public interface InitiateStkPushPort {
    StkPushResponse execute(InitiateStkPushRequest request, UUID initiatedBy);
}
