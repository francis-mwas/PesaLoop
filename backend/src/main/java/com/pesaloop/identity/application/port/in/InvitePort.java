package com.pesaloop.identity.application.port.in;

import com.pesaloop.identity.application.usecase.AuthUseCase.InviteResult;
import com.pesaloop.identity.application.port.out.UserRepository.UserGroupMembership;
import java.util.List;
import java.util.UUID;

/** Input port — invite acceptance and group membership query. */
public interface InvitePort {
    InviteResult acceptInvite(String token, String password);
    List<UserGroupMembership> getMyGroups(UUID userId);
}
