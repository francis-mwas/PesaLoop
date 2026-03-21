package com.pesaloop.group.application.port.out;

public interface SlugGenerator {
    /**
     * Generates a URL-friendly slug from a group name.
     * E.g. "Wanjiku Welfare Group 2024" → "wanjiku-welfare-group-2024"
     * Implementations must ensure uniqueness.
     */
    String generate(String groupName);
}
