package com.swiftpay.repository;

import com.swiftpay.domain.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long>, JpaSpecificationExecutor<Transfer> {

    /**
     * Inserts a PENDING transfer unless (customer_id, idempotency_key) already exists.
     * Returns 1 if inserted, 0 if the key was already taken. A concurrent duplicate blocks on the
     * unique index until the first transaction finishes, then gets 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO transfer (customer_id, source_account_id, receiver_account_id, receiver_iban, amount,
                                  currency, description, status, idempotency_key, request_hash, created_at)
            VALUES (:customerId, :sourceAccountId, CAST(:receiverAccountId AS BIGINT), :receiverIban, :amount,
                    'TRY', CAST(:description AS VARCHAR), 'PENDING', :idempotencyKey, :requestHash, :createdAt)
            ON CONFLICT (customer_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertPendingIfAbsent(@Param("customerId") Long customerId,
                              @Param("sourceAccountId") Long sourceAccountId,
                              @Param("receiverAccountId") Long receiverAccountId,
                              @Param("receiverIban") String receiverIban,
                              @Param("amount") BigDecimal amount,
                              @Param("description") String description,
                              @Param("idempotencyKey") String idempotencyKey,
                              @Param("requestHash") String requestHash,
                              @Param("createdAt") Instant createdAt);

    Optional<Transfer> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    /** Ownership-scoped lookup: another customer's transfer is simply not found. */
    Optional<Transfer> findByIdAndCustomerId(Long id, Long customerId);

    /** Sum of COMPLETED transfers with completedAt in [fromInclusive, toExclusive); 0 if none. */
    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transfer t
            where t.customerId = :customerId
              and t.status = com.swiftpay.domain.TransferStatus.COMPLETED
              and t.completedAt >= :fromInclusive
              and t.completedAt < :toExclusive
            """)
    BigDecimal sumCompletedAmount(@Param("customerId") Long customerId,
                                  @Param("fromInclusive") Instant fromInclusive,
                                  @Param("toExclusive") Instant toExclusive);

    /**
     * Count and sum of COMPLETED transfers per month (in {@code zone}, e.g. Europe/Istanbul) since
     * {@code since}, newest month first. Empty months are not returned; zero-filling is the service's job.
     */
    @Query(value = """
            SELECT to_char(date_trunc('month', t.completed_at AT TIME ZONE :zone), 'YYYY-MM') AS "month",
                   COUNT(*)                                                             AS "transferCount",
                   COALESCE(SUM(t.amount), 0)                                           AS "totalAmount"
            FROM transfer t
            WHERE t.customer_id = :customerId
              AND t.status = 'COMPLETED'
              AND t.completed_at >= :since
            GROUP BY 1
            ORDER BY 1 DESC
            """, nativeQuery = true)
    List<MonthlyTransferStats> monthlyCompletedStats(@Param("customerId") Long customerId,
                                                     @Param("since") Instant since,
                                                     @Param("zone") String zone);
}
