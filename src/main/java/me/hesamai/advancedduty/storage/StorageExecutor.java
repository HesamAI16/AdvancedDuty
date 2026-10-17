package me.hesamai.advancedduty.storage;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-threaded executor for every disk / database operation. Because it is one ordered queue, a
 * save queued when a player quits is always finished before the load queued when he rejoins, and two
 * writes for the same player can never interleave (which used to corrupt YAML files).
 */
public final class StorageExecutor {

    private final ExecutorService service;
    private final Logger logger;

    public StorageExecutor(Logger logger) {
        this.logger = logger;
        this.service = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AdvancedDuty-Storage");
            t.setDaemon(false);
            return t;
        });
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            service.execute(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
        return future;
    }

    /** Fire-and-forget; failures are logged. */
    public void execute(String what, Runnable task) {
        try {
            service.execute(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[Storage] " + what + " failed: " + t.getMessage(), t);
                }
            });
        } catch (RejectedExecutionException ex) {
            // already shutting down: run inline so the data is not lost
            try {
                task.run();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[Storage] " + what + " failed during shutdown: " + t.getMessage(), t);
            }
        }
    }

    /** Waits for the queue to drain. Call from onDisable. */
    public void shutdown(long timeoutSeconds) {
        service.shutdown();
        try {
            if (!service.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.warning("[Storage] Timed out while flushing pending saves (" + timeoutSeconds + "s).");
                service.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            service.shutdownNow();
        }
    }
}
