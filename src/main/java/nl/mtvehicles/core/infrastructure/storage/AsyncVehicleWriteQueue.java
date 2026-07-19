package nl.mtvehicles.core.infrastructure.storage;

import nl.mtvehicles.core.Main;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Coalescing single-writer queue. Newer data for a license plate replaces older queued data. */
public final class AsyncVehicleWriteQueue {
    private final MariaDbVehicleStorage storage;
    private final Map<String, StoredVehicle> pendingUpserts = new ConcurrentHashMap<>();
    private final Set<String> pendingDeletes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ExecutorService executor;
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final AtomicLong lastErrorLog = new AtomicLong(0L);

    public AsyncVehicleWriteQueue(MariaDbVehicleStorage storage) {
        this.storage = storage;
        this.executor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("MTVehicles-DB-Writer").daemon(true).factory());
    }

    public void enqueue(Collection<StoredVehicle> upserts, Collection<String> deletes) {
        for (StoredVehicle vehicle : upserts) {
            pendingDeletes.remove(vehicle.licensePlate());
            pendingUpserts.put(vehicle.licensePlate(), vehicle);
        }
        for (String licensePlate : deletes) {
            pendingUpserts.remove(licensePlate);
            pendingDeletes.add(licensePlate);
        }
    }

    public void requestFlush() {
        if (!hasPendingWrites() || !flushScheduled.compareAndSet(false, true)) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean flushed = false;
                try {
                    flushed = flushOnce();
                } finally {
                    flushScheduled.set(false);
                    // Writes may arrive while a batch is in progress. Ensure
                    // they are flushed immediately instead of waiting for the
                    // next periodic save tick.
                    if (flushed && hasPendingWrites()) requestFlush();
                }
            }
        });
    }

    private boolean flushOnce() {
        Map<String, StoredVehicle> upserts = new HashMap<>(pendingUpserts);
        Set<String> deletes = new HashSet<>(pendingDeletes);
        if (upserts.isEmpty() && deletes.isEmpty()) return true;

        try {
            storage.writeBatch(upserts.values(), deletes);
            for (Map.Entry<String, StoredVehicle> entry : upserts.entrySet()) {
                pendingUpserts.remove(entry.getKey(), entry.getValue());
            }
            pendingDeletes.removeAll(deletes);
            return true;
        } catch (SQLException exception) {
            long now = System.currentTimeMillis();
            long previous = lastErrorLog.get();
            if (now - previous >= 30000L && lastErrorLog.compareAndSet(previous, now)) {
                Main.instance.getLogger().severe("MariaDB write failed; data remains queued for retry: " + exception.getMessage());
            }
            return false;
        }
    }

    public boolean flushBlocking(long timeout, TimeUnit unit) {
        try {
            Future<?> future = executor.submit(new Runnable() {
                @Override
                public void run() {
                    flushOnce();
                }
            });
            future.get(timeout, unit);
            return !hasPendingWrites();
        } catch (Exception exception) {
            Main.instance.getLogger().severe("Timed out while flushing MariaDB vehicle data: " + exception.getMessage());
            return false;
        }
    }

    public boolean hasPendingWrites() {
        return !pendingUpserts.isEmpty() || !pendingDeletes.isEmpty();
    }

    public int getPendingWriteCount() {
        return pendingUpserts.size() + pendingDeletes.size();
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        storage.close();
    }
}
