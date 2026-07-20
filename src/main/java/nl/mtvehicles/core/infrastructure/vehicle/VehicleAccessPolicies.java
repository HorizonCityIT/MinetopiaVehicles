package nl.mtvehicles.core.infrastructure.vehicle;

import nl.mtvehicles.core.Main;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Registry interno delle policy di accesso registrate da plugin esterni.
 */
final class VehicleAccessPolicies {
    private static final Map<String, BiPredicate<String, Player>> DRIVING_POLICIES = new ConcurrentHashMap<>();
    private static final Map<String, BiPredicate<String, Player>> ENTRY_OVERRIDES = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

    private VehicleAccessPolicies() { }

    static void registerDrivingPolicy(String key, BiPredicate<String, Player> policy) {
        DRIVING_POLICIES.put(requireKey(key), requirePolicy(policy));
        REPORTED_FAILURES.remove("drive:" + key);
    }

    static void registerEntryOverride(String key, BiPredicate<String, Player> override) {
        ENTRY_OVERRIDES.put(requireKey(key), requirePolicy(override));
        REPORTED_FAILURES.remove("entry:" + key);
    }

    static void unregister(String key) {
        if (key == null) return;
        DRIVING_POLICIES.remove(key);
        ENTRY_OVERRIDES.remove(key);
        REPORTED_FAILURES.remove("drive:" + key);
        REPORTED_FAILURES.remove("entry:" + key);
    }

    static boolean isDrivingAllowed(String licensePlate, Player player) {
        for (var entry : DRIVING_POLICIES.entrySet()) {
            try {
                if (!entry.getValue().test(licensePlate, player)) return false;
            } catch (RuntimeException exception) {
                reportOnce("drive", entry.getKey(), exception);
            }
        }
        return true;
    }

    static boolean hasEntryOverride(String licensePlate, Player player) {
        for (var entry : ENTRY_OVERRIDES.entrySet()) {
            try {
                if (entry.getValue().test(licensePlate, player)) return true;
            } catch (RuntimeException exception) {
                reportOnce("entry", entry.getKey(), exception);
            }
        }
        return false;
    }

    static void clear() {
        DRIVING_POLICIES.clear();
        ENTRY_OVERRIDES.clear();
        REPORTED_FAILURES.clear();
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Policy key cannot be blank");
        return key;
    }

    private static BiPredicate<String, Player> requirePolicy(BiPredicate<String, Player> policy) {
        if (policy == null) throw new IllegalArgumentException("Policy cannot be null");
        return policy;
    }

    private static void reportOnce(String type, String key, RuntimeException exception) {
        if (Main.instance != null && REPORTED_FAILURES.add(type + ':' + key)) {
            Main.logWarning("Vehicle access policy '" + key + "' failed: " + exception.getMessage());
        }
    }
}
