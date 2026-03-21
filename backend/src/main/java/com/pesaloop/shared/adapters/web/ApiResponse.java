package com.pesaloop.shared.adapters.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Standard API response envelope.
 * All endpoints return this wrapper for consistency.
 *
 * Success:  { "success": true,  "data": {...},  "message": "...", "timestamp": "..." }
 * Error:    { "success": false, "error": "...", "code": "...",   "timestamp": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String error,
        String code,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String error, String code) {
        return new ApiResponse<>(false, null, null, error, code, Instant.now());
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, null, error, "ERROR", Instant.now());
    }
}
