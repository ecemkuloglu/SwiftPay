package com.swiftpay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/auth/login. Password max 72 because BCrypt ignores everything after 72 bytes. */
public record LoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 72) String password) {

    /** Never print the password, e.g. in a debug log of the request. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
