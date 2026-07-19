package nl.mtvehicles.core.infrastructure.dependencies;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Isolates direct ItemsAdder API access so MTVehicles can still load when the
 * optional dependency is not installed.
 */
public final class ItemsAdderUtils {
    private ItemsAdderUtils() {}

    /**
     * Resolve an ItemsAdder custom item by its {@code namespace:item} ID.
     *
     * @param namespacedId ItemsAdder namespaced item ID
     * @return a cloned Bukkit item, or {@code null} when the ID is not registered
     */
    @Nullable
    public static ItemStack getItemStack(String namespacedId) {
        CustomStack customStack = CustomStack.getInstance(namespacedId);
        if (customStack == null || customStack.getItemStack() == null) return null;
        return customStack.getItemStack().clone();
    }
}
