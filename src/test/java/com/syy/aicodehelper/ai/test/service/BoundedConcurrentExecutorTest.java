package com.syy.aicodehelper.ai.test.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedConcurrentExecutorTest {
    @Test
    void limitsConcurrencyAndIsolatesIndividualCaseFailures() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        try {
            List<BoundedConcurrentExecutor.Outcome<Integer, Integer>> results =
                    new BoundedConcurrentExecutor().execute(List.of(1, 2, 3, 4, 5, 6), 3, pool, value -> {
                        int running = active.incrementAndGet();
                        maximum.accumulateAndGet(running, Math::max);
                        try {
                            try {
                                Thread.sleep(60);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("test task interrupted", interrupted);
                            }
                            if (value == 4) throw new IllegalStateException("simulated case failure");
                            return value * 10;
                        } finally {
                            active.decrementAndGet();
                        }
                    });

            assertEquals(6, results.size());
            assertTrue(maximum.get() > 1, "more than one case should run concurrently");
            assertTrue(maximum.get() <= 3, "per-task concurrency must not exceed the configured cap");
            assertEquals(1, results.stream().filter(result -> !result.succeeded()).count());
            assertEquals(5, results.stream().filter(BoundedConcurrentExecutor.Outcome::succeeded).count());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
}
