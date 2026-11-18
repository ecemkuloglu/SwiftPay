package com.swiftpay.domain;

import com.swiftpay.exception.InsufficientBalanceException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "iban", nullable = false, length = 26, updatable = false)
    private String iban;

    /** CHAR(3) in the DB: mapped as CHAR so schema validation sees bpchar, not varchar. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Account(Long customerId, String iban, BigDecimal openingBalance, Instant now) {
        if (openingBalance == null || openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("opening balance must not be negative");
        }
        this.customerId = customerId;
        this.iban = iban;
        this.currency = Money.CURRENCY;
        this.balance = openingBalance.setScale(Money.SCALE);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Removes {@code amount}; throws {@link InsufficientBalanceException} if the balance would go negative. */
    public void debit(BigDecimal amount, Instant now) {
        BigDecimal value = Money.requirePositive(amount);
        if (balance.compareTo(value) < 0) {
            throw new InsufficientBalanceException();
        }
        balance = balance.subtract(value);
        updatedAt = now;
    }

    public void credit(BigDecimal amount, Instant now) {
        balance = balance.add(Money.requirePositive(amount));
        updatedAt = now;
    }
}
