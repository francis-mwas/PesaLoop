package com.pesaloop.group.domain.model;

public enum MemberStatus {
    ACTIVE,
    DORMANT,        // has not contributed in N cycles but not formally exited
    SUSPENDED,      // temporarily barred (e.g. unpaid fines or loans)
    EXITED,         // formally left the group — savings returned
    DECEASED        // special status for welfare claim processing
}
