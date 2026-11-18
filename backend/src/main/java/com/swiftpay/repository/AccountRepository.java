package com.swiftpay.repository;

import com.swiftpay.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** All accounts of a customer, ordered by id for a stable API response. */
    @Query("select a from Account a where a.customerId = :customerId order by a.id asc")
    List<Account> findAllByCustomerId(@Param("customerId") Long customerId);

    /** Ownership check that does not load the entity into the persistence context. */
    boolean existsByIdAndCustomerId(Long id, Long customerId);

    /** Receiver lookup without a lock. */
    @Query("select a.id from Account a where a.iban = :iban")
    Optional<Long> findIdByIban(@Param("iban") String iban);

    /**
     * SELECT ... ORDER BY id ASC FOR UPDATE. PostgreSQL sorts before locking, so rows are always
     * locked in ascending id order and two opposite transfers (A->B, B->A) cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id asc")
    List<Account> lockByIdsOrdered(@Param("ids") Collection<Long> ids);
}
