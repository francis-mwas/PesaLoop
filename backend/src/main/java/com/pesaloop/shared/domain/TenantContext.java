package com.pesaloop.shared.domain;

import java.util.UUID;

/**
 * Thread-local holder for the current tenant (group) context.
 * Set by JwtAuthenticationFilter on every request.
 * All repository queries automatically scope to this groupId.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_GROUP = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER  = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID groupId, UUID userId, String role) {
        CURRENT_GROUP.set(groupId);
        CURRENT_USER.set(userId);
        CURRENT_ROLE.set(role);
    }

    /** Returns null instead of throwing when no group context is set (e.g. super-admin calls) */
    public static UUID getGroupIdOrNull() {
        try { return getGroupId(); } catch (Exception e) { return null; }
    }

    public static UUID getGroupId() {
        UUID id = CURRENT_GROUP.get();
        if (id == null) throw new IllegalStateException("No tenant context set on current thread");
        return id;
    }

    public static UUID getUserId() {
        UUID id = CURRENT_USER.get();
        if (id == null) throw new IllegalStateException("No user context set on current thread");
        return id;
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    /** Returns true if there is an active tenant context (useful in super-admin endpoints). */
    public static boolean hasTenant() {
        return CURRENT_GROUP.get() != null;
    }

    public static void clear() {
        CURRENT_GROUP.remove();
        CURRENT_USER.remove();
        CURRENT_ROLE.remove();
    }
}
