package com.swiftpay.repository;

import com.swiftpay.domain.Transfer;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for the transfer list. A Specification adds a date predicate only when the bound is present,
 * which avoids "(:p is null or ...)" JPQL and PostgreSQL's untyped-null parameter problem.
 */
public final class TransferSpecifications {

    private TransferSpecifications() {
    }

    /** Transfers of one customer with createdAt in [startInclusive, endExclusive); either bound may be null. */
    public static Specification<Transfer> forCustomer(Long customerId, Instant startInclusive, Instant endExclusive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            if (startInclusive != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startInclusive));
            }
            if (endExclusive != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), endExclusive));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
