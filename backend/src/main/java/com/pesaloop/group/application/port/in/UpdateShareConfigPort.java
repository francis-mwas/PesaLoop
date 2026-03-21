package com.pesaloop.group.application.port.in;

import com.pesaloop.group.application.usecase.UpdateShareConfigUseCase.GroupShareConfigResponse;
import com.pesaloop.group.application.usecase.UpdateShareConfigUseCase.UpdateShareConfigRequest;
import java.util.UUID;

/** Input port — update a group's share price and configuration. */
public interface UpdateShareConfigPort {
    GroupShareConfigResponse execute(UUID groupId, UpdateShareConfigRequest request, UUID updatedByUserId);
}
