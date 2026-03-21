package com.pesaloop.group.domain.model;

public enum MemberRole {
    /**
     * Full administrative access to the group.
     * Can configure settings, approve loans, manage members.
     */
    ADMIN,

    /**
     * Manages finances: records contributions, approves payouts.
     * Cannot change group settings or manage loans.
     */
    TREASURER,

    /**
     * Manages records, communications, meeting minutes.
     * Read-only on financial data.
     */
    SECRETARY,

    /**
     * Standard member: can view own data, apply for loans, contribute.
     */
    MEMBER,

    /**
     * Read-only access. For external auditors or oversight committee members.
     */
    AUDITOR
}
