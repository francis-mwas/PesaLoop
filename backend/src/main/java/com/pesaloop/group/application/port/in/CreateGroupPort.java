package com.pesaloop.group.application.port.in;

import com.pesaloop.group.application.dto.CreateGroupRequest;
import com.pesaloop.group.application.dto.GroupResponse;
import java.util.UUID;

/** Input port — create a new chamaa group. */
public interface CreateGroupPort {
    GroupResponse execute(CreateGroupRequest request, UUID createdByUserId);
}
