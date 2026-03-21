package com.pesaloop.payment.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port — resolves a paying member from a C2B paybill reference.
 * C2bPaymentUseCase depends on this, not on JdbcTemplate.
 *
 * Strategy cascade: member number → phone → user phone → name.
 */
public interface MemberResolutionRepository {

    Optional<MemberRef> findByMemberNumber(UUID groupId, String memberNumber);
    Optional<MemberRef> findByPhone(UUID groupId, String phone);
    Optional<MemberRef> findByUserPhone(UUID groupId, String phone);
    Optional<MemberRef> findByNamePartial(UUID groupId, String namePart);
    Optional<GroupRef>  findGroupByShortcode(String shortcode);

    record MemberRef(UUID memberId, String memberNumber) {}
    record GroupRef(UUID groupId) {}
}
