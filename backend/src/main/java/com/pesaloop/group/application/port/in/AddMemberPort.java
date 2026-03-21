package com.pesaloop.group.application.port.in;

import com.pesaloop.group.application.usecase.AddMemberUseCase.AddMemberRequest;
import com.pesaloop.group.application.usecase.AddMemberUseCase.AddMemberResponse;
import java.util.UUID;

/** Input port — add a member to a group. */
public interface AddMemberPort {
    AddMemberResponse execute(AddMemberRequest req, UUID addedByUserId);
}
