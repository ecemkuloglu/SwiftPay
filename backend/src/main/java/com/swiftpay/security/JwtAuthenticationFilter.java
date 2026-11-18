package com.swiftpay.security;

import com.swiftpay.repository.CustomerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates "Authorization: Bearer &lt;jwt&gt;" requests. Any problem (expired, tampered, wrong signature,
 * customer deleted) simply leaves the request unauthenticated; the entry point then answers with the same 401.
 * Not a Spring bean on purpose, so it is not also registered as a plain servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final CustomerRepository customerRepository;

    public JwtAuthenticationFilter(JwtService jwtService, CustomerRepository customerRepository) {
        this.jwtService = jwtService;
        this.customerRepository = customerRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            jwtService.verify(header.substring(BEARER.length()).trim())
                    .filter(customerRepository::existsById)
                    .ifPresent(customerId -> SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(
                                    new AuthenticatedCustomer(customerId), null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
