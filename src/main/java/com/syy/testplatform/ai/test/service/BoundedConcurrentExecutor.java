package com.syy.testplatform.ai.test.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.function.Function;

/** Executes a bounded number of items concurrently and isolates an individual item's failure. */
public class BoundedConcurrentExecutor {

    public <T, R> List<Outcome<T, R>> execute(List<T> items, int maxConcurrency,
                                               Executor executor, Function<T, R> action) {
        if (items == null || items.isEmpty()) return List.of();
        if (executor == null || action == null) throw new IllegalArgumentException("executor/action 不能为空");
        int limit = Math.max(1, Math.min(maxConcurrency, items.size()));
        CompletionService<Outcome<T, R>> completion = new ExecutorCompletionService<>(executor);
        List<Outcome<T, R>> outcomes = new ArrayList<>(items.size());
        int next = 0;
        int active = 0;

        while (next < items.size() && active < limit) {
            submit(completion, items.get(next++), action);
            active++;
        }

        while (active > 0) {
            try {
                Future<Outcome<T, R>> completed = completion.take();
                outcomes.add(completed.get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("并发执行被中断", ex);
            } catch (Exception ex) {
                // The submitted wrapper catches business exceptions; this branch protects executor failures.
                outcomes.add(new Outcome<>(null, null, ex));
            }
            active--;
            if (next < items.size()) {
                submit(completion, items.get(next++), action);
                active++;
            }
        }
        return outcomes;
    }

    private <T, R> void submit(CompletionService<Outcome<T, R>> completion, T item,
                               Function<T, R> action) {
        completion.submit(() -> {
            try {
                return new Outcome<>(item, action.apply(item), null);
            } catch (Exception error) {
                return new Outcome<>(item, null, error);
            }
        });
    }

    public record Outcome<T, R>(T item, R result, Throwable error) {
        public boolean succeeded() { return error == null; }
    }
}
