package nl.mtvehicles.core.infrastructure.dataconfig;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Allocates the lowest available Horizon Roleplay license plate. */
final class LicensePlateAllocator {
    private static final Pattern HORIZON_PLATE = Pattern.compile("^hz(\\d{3})([a-z]{2})$");
    private static final int FIRST_NUMBER = 1;
    private static final int LAST_NUMBER = 999;
    private static final int FIRST_SUFFIX = suffixIndex("rp");
    private static final int LAST_SUFFIX = suffixIndex("zz");

    private final Map<String, BitSet> occupiedNumbersBySuffix = new HashMap<>();
    private final Set<String> registeredPlates = new HashSet<>();
    private final Set<String> reservedPlates = new HashSet<>();

    void reset(Collection<String> plates) {
        occupiedNumbersBySuffix.clear();
        registeredPlates.clear();
        reservedPlates.clear();
        for (String plate : plates) {
            if (plate == null || plate.isBlank()) continue;
            String normalized = normalize(plate);
            if (registeredPlates.add(normalized)) setPatternOccupied(normalized, true);
        }
    }

    String reserveNext() {
        for (int suffixIndex = FIRST_SUFFIX; suffixIndex <= LAST_SUFFIX; suffixIndex++) {
            String suffix = suffixFromIndex(suffixIndex);
            BitSet occupied = occupiedNumbersBySuffix.computeIfAbsent(suffix, ignored -> new BitSet(LAST_NUMBER + 1));
            int number = occupied.nextClearBit(FIRST_NUMBER);
            if (number > LAST_NUMBER) continue;

            String plate = "hz%03d%s".formatted(number, suffix);
            occupied.set(number);
            reservedPlates.add(plate);
            return plate;
        }
        throw new IllegalStateException("All Horizon Roleplay license plates from hz001rp to hz999zz are occupied");
    }

    boolean isRegistered(String plate) {
        return plate != null && registeredPlates.contains(normalize(plate));
    }

    boolean isUnavailable(String plate) {
        if (plate == null) return false;
        String normalized = normalize(plate);
        return registeredPlates.contains(normalized) || reservedPlates.contains(normalized);
    }

    void addRegistered(String plate) {
        String normalized = normalizeRequired(plate);
        if (!registeredPlates.add(normalized)) {
            throw new IllegalStateException("Vehicle " + plate + " is already registered");
        }
        reservedPlates.remove(normalized);
        setPatternOccupied(normalized, true);
    }

    void removeRegistered(String plate) {
        if (plate == null) return;
        String normalized = normalize(plate);
        if (!registeredPlates.remove(normalized)) return;
        if (!reservedPlates.contains(normalized)) setPatternOccupied(normalized, false);
    }

    void releaseReservation(String plate) {
        if (plate == null) return;
        String normalized = normalize(plate);
        if (!reservedPlates.remove(normalized)) return;
        if (!registeredPlates.contains(normalized)) setPatternOccupied(normalized, false);
    }

    private void setPatternOccupied(String normalizedPlate, boolean occupied) {
        Matcher matcher = HORIZON_PLATE.matcher(normalizedPlate);
        if (!matcher.matches()) return;

        int number = Integer.parseInt(matcher.group(1));
        String suffix = matcher.group(2);
        BitSet numbers = occupiedNumbersBySuffix.computeIfAbsent(suffix, ignored -> new BitSet(LAST_NUMBER + 1));
        if (occupied) {
            numbers.set(number);
        } else {
            numbers.clear(number);
            if (numbers.isEmpty()) occupiedNumbersBySuffix.remove(suffix);
        }
    }

    private static String normalizeRequired(String plate) {
        if (plate == null || plate.isBlank()) throw new IllegalArgumentException("Missing license plate");
        return normalize(plate);
    }

    private static String normalize(String plate) {
        return plate.toLowerCase(Locale.ROOT);
    }

    private static int suffixIndex(String suffix) {
        return (suffix.charAt(0) - 'a') * 26 + suffix.charAt(1) - 'a';
    }

    private static String suffixFromIndex(int index) {
        return new String(new char[]{(char) ('a' + index / 26), (char) ('a' + index % 26)});
    }
}
