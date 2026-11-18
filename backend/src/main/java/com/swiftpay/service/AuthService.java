package com.swiftpay.service;

import com.swiftpay.domain.Customer;
import com.swiftpay.dto.LoginResponse;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.repository.CustomerRepository;
import com.swiftpay.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Username/password login. Unknown user and wrong password give the same INVALID_CREDENTIALS error and take
 * about the same time: for an unknown user the password is still checked against a dummy BCrypt hash.
 * Too many failures for one username + client IP give TOO_MANY_ATTEMPTS (even with the right password).
 */
@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogger auditLogger;
    private final RateLimiter rateLimiter;
    private final String dummyHash;

    public AuthService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditLogger auditLogger, RateLimiter rateLimiter) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogger = auditLogger;
        this.rateLimiter = rateLimiter;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(String username, String password, String clientIp) {
        rateLimiter.checkLogin(username, clientIp);

        Optional<Customer> customer = customerRepository.findByUsername(username);
        String hash = customer.map(Customer::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(password, hash);

        if (customer.isEmpty() || !passwordMatches) {
            rateLimiter.recordLoginFailure(username, clientIp);
            auditLogger.loginFailed(customer.map(Customer::getId).orElse(null));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        rateLimiter.resetLogin(username, clientIp);
        return LoginResponse.bearer(jwtService.issue(customer.get().getId()), jwtService.expirationSeconds());
    }
}
