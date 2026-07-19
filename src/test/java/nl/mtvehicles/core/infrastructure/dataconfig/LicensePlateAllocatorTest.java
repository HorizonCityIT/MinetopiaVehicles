package nl.mtvehicles.core.infrastructure.dataconfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicensePlateAllocatorTest {
    @Test
    void reusesTheLowestAvailableNumber() {
        LicensePlateAllocator allocator = new LicensePlateAllocator();
        allocator.reset(List.of("hz001rp", "hz003rp", "legacy-plate"));

        assertEquals("hz002rp", allocator.reserveNext());
    }

    @Test
    void existingPlatesAreCaseInsensitive() {
        LicensePlateAllocator allocator = new LicensePlateAllocator();
        allocator.reset(List.of("HZ001RP"));

        assertTrue(allocator.isRegistered("hz001rp"));
        assertEquals("hz002rp", allocator.reserveNext());
    }

    @Test
    void releasedReservationCanBeReused() {
        LicensePlateAllocator allocator = new LicensePlateAllocator();
        allocator.reset(List.of());
        String first = allocator.reserveNext();

        allocator.releaseReservation(first);

        assertEquals("hz001rp", allocator.reserveNext());
    }

    @Test
    void deletedRegisteredPlateCanBeReused() {
        LicensePlateAllocator allocator = new LicensePlateAllocator();
        allocator.reset(List.of("hz001rp", "hz002rp"));

        allocator.removeRegistered("hz001rp");

        assertEquals("hz001rp", allocator.reserveNext());
    }

    @Test
    void committedReservationRemainsOccupied() {
        LicensePlateAllocator allocator = new LicensePlateAllocator();
        allocator.reset(List.of());
        String first = allocator.reserveNext();

        allocator.addRegistered(first);
        allocator.releaseReservation(first);

        assertEquals("hz002rp", allocator.reserveNext());
    }

    @Test
    void advancesTheSuffixAfter999() {
        LicensePlateAllocator allocator = new LicensePlateAllocator();
        List<String> occupied = new ArrayList<>(999);
        for (int number = 1; number <= 999; number++) {
            occupied.add("hz%03drp".formatted(number));
        }
        allocator.reset(occupied);

        assertEquals("hz001rq", allocator.reserveNext());
    }
}
