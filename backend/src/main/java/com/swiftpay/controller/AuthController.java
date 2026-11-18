package com.swiftpay.controller;

import com.swiftpay.dto.ApiResponse;
import com.swiftpay.dto.LoginRequest;
import com.swiftpay.dto.LoginResponse;
import com.swiftpay.exception.ApiResponseFactory;
import com.swiftpay.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ApiResponseFactory responses;

    @Operation(summary = "Log in with username and password; returns a short-lived bearer token")
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // client IP: TCP peer, or X-Forwarded-For when the peer is a trusted proxy (TRUSTED_PROXIES, Tomcat RemoteIpValve)
        return responses.success(authService.login(request.username(), request.password(), http.getRemoteAddr()));
    }
}
