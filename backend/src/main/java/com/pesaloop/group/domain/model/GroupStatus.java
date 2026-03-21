package com.pesaloop.group.domain.model;

public enum GroupStatus {
    PENDING_SETUP,  // group created but not yet configured / activated
    ACTIVE,
    SUSPENDED,      // temporarily disabled (e.g. non-payment of platform subscription)
    CLOSED          // permanently closed, data retained for audit
}
