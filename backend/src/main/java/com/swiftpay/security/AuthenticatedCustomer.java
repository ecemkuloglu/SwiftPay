package com.swiftpay.security;

/** Principal of an authenticated request. The customer id comes only from a verified JWT. */
public record AuthenticatedCustomer(Long customerId) {
}
