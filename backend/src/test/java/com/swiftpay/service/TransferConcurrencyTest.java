package com.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftpay.domain.Account;
import com.swiftpay.domain.Customer;
import com.swiftpay.dto.TransferResult;
import com.swiftpay.exception.BusinessException;
import com.swiftpay.exception.ErrorCode;
import com.swiftpay.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Real parallel transactions against PostgreSQL (no test transaction). All threads wait on a latch and
 * start together. Every scenario is repeated to catch flakiness.
 */
class TransferConcurrencyTest extends IntegrationTestBase {

    private static final int REPEAT = 5;

    /** Success (result) or business failure (error code) of one call; anything else fails the test. */
    private record Outcome(TransferResult result, ErrorCode error) {
    }

    @RepeatedTest(REPEAT)
    void parallelTransfersFromOneAccountNeverOverdraw() throws Exception {
        Customer customer = customer();
        Account source = account(customer, "1000.00");          // covers 10 of 20 transfers
        Account receiver = account(customer(), "0.00");

        List<Outcome> outcomes = runConcurrently(20, i ->
                () -> transferService.createTransfer(customer.getId(), newKey(), request(source, receiver, "100.00")));

        long completed = outcomes.stream().filter(o -> o.result() != null).count();
        long failed = outcomes.stream().filter(o -> o.error() != null).count();
        BigDecimal completedSum = outcomes.stream().filter(o -> o.result() != null)
                .map(o -> o.result().transfer().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalBalance = balance(source);

        assertThat(completed + failed).isEqualTo(20);
        assertThat(completed).isEqualTo(10);
        assertThat(outcomes).filteredOn(o -> o.error() != null)
                .allSatisfy(o -> assertThat(o.error()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
        assertThat(finalBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(new BigDecimal("1000.00").subtract(finalBalance)).isEqualByComparingTo(completedSum);
        assertThat(balance(receiver)).isEqualByComparingTo(completedSum);
        assertThat(countTransfers(customer, "COMPLETED")).isEqualTo(completed);
        assertThat(countTransfers(customer, "FAILED")).isEqualTo(failed);
    }

    @RepeatedTest(REPEAT)
    void parallelTransfersFromTwoAccountsNeverExceedTheDailyLimit() throws Exception {
        Customer customer = customer();
        Account first = account(customer, "300000.00");
        Account second = account(customer, "300000.00");
        // a different receiver per transfer, so no shared account lock serialises them: only the
        // customer-row lock prevents two transactions from reading the same "used today" value
        List<Account> receivers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            receivers.add(account(customer(), "0.00"));
        }

        List<Outcome> outcomes = runConcurrently(10, i -> () -> transferService.createTransfer(customer.getId(),
                newKey(), request(i % 2 == 0 ? first : second, receivers.get(i), "20000.00")));

        long completed = outcomes.stream().filter(o -> o.result() != null).count();
        BigDecimal completedSum = outcomes.stream().filter(o -> o.result() != null)
                .map(o -> o.result().transfer().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(completed).isLessThanOrEqualTo(5).isEqualTo(5);
        assertThat(completedSum).isLessThanOrEqualTo(new BigDecimal("100000.00"));
        assertThat(outcomes).filteredOn(o -> o.error() != null)
                .allSatisfy(o -> assertThat(o.error()).isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED));
        assertThat(balance(first).add(balance(second))).isEqualByComparingTo("500000.00");
    }

    @RepeatedTest(REPEAT)
    void parallelRequestsWithTheSameKeyCreateExactlyOneTransfer() throws Exception {
        Customer customer = customer();
        Account source = account(customer, "1000.00");
        Account receiver = account(customer(), "0.00");
        String key = newKey();

        List<Outcome> outcomes = runConcurrently(8, i ->
                () -> transferService.createTransfer(customer.getId(), key, request(source, receiver, "100.00")));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.result()).isNotNull());
        assertThat(outcomes).extracting(o -> o.result().transfer().id()).containsOnly(
                outcomes.get(0).result().transfer().id());
        assertThat(outcomes).filteredOn(o -> !o.result().replayed()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE customer_id = ?", Long.class,
                customer.getId())).isEqualTo(1);
        assertThat(balance(source)).isEqualByComparingTo("900.00");
    }

    @RepeatedTest(REPEAT)
    void oppositeDirectionsDoNotDeadlockOrLoseUpdates() throws Exception {
        Customer alice = customer();
        Customer bob = customer();
        Account a = account(alice, "1000.00");
        Account b = account(bob, "1000.00");

        // 15 A->B of 1.00 and 15 B->A of 2.00, interleaved
        List<Outcome> outcomes = runConcurrently(30, i -> i % 2 == 0
                ? () -> transferService.createTransfer(alice.getId(), newKey(), request(a, b, "1.00"))
                : () -> transferService.createTransfer(bob.getId(), newKey(), request(b, a, "2.00")));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(balance(a)).isEqualByComparingTo("1015.00");   // 1000 - 15*1 + 15*2
        assertThat(balance(b)).isEqualByComparingTo("985.00");    // 1000 + 15*1 - 15*2
        assertThat(balance(a).add(balance(b))).isEqualByComparingTo("2000.00");
    }

    /**
     * Starts {@code n} tasks at the same moment and waits for all of them. A business exception becomes an
     * Outcome; any other exception (deadlock, timeout, bug) is rethrown and fails the test.
     */
    private static List<Outcome> runConcurrently(int n, IntFunction<Callable<TransferResult>> taskFactory)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<TransferResult> task = taskFactory.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome(task.call(), null);
                    } catch (BusinessException e) {
                        return new Outcome(null, e.getErrorCode());
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
