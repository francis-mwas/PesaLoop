package com.pesaloop.group.domain.model;

/**
 * How often members are expected to contribute.
 *
 * Real-world chamaa patterns observed:
 * - DAILY:      bodaboda / matatu saccos, small daily hawker groups
 * - WEEKLY:     most urban welfare groups
 * - FORTNIGHTLY: less common but used
 * - MONTHLY:    the most common for table-banking chamaas (like the KES 3,000/share example)
 * - QUARTERLY:  larger investment groups
 * - ANNUALLY:   year-end lump-sum groups (rare)
 * - CUSTOM:     admin sets exact interval in days (e.g. every 10 days)
 */
public enum ContributionFrequency {
    DAILY,
    WEEKLY,
    FORTNIGHTLY,        // every 14 days
    MONTHLY,
    QUARTERLY,
    ANNUALLY,
    CUSTOM              // uses customIntervalDays field on GroupConfig
}
