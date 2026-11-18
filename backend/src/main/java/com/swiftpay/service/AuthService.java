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
 */
@Service
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogger auditLogger;
    private final String dummyHash;

    public AuthService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditLogger auditLogger) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogger = auditLogger;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(String username, String password) {
        Optional<Customer> customer = customerRepository.findByUsername(username);
        String hash = customer.map(Customer::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(password, hash);

        if (customer.isEmpty() || !passwordMatches) {
            auditLogger.loginFailed(customer.map(Customer::getId).orElse(null));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return LoginResponse.bearer(jwtService.issue(customer.get().getId()), jwtService.expirationSeconds());
    }
}
