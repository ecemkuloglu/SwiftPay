package com.swiftpay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.exception.ApiResponseFactory;
import com.swiftpay.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Security-layer errors in the standard ErrorResponse shape. Both missing and invalid credentials, and an
 * access-denied decision, answer 401 UNAUTHORIZED with one generic message (nothing about why).
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiResponseFactory responses;
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        write(request, response);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(request, response);
    }

    private void write(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), responses.error(ErrorCode.UNAUTHORIZED, request));
    }
}
