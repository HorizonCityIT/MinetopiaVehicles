package nl.mtvehicles.core.events;

import nl.mtvehicles.core.events.interfaces.HasVehicle;
import nl.mtvehicles.core.events.interfaces.IsCancellable;
import nl.mtvehicles.core.infrastructure.models.MTVEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * On horn use
 */
public class HornUseEvent extends MTVEvent implements IsCancellable, Cancellable, HasVehicle {
    private static final HandlerList HANDLERS = new HandlerList();
    final private String licensePlate;

    public HornUseEvent(String licensePlate){
        this.licensePlate = licensePlate;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String getLicensePlate(){
        return licensePlate;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

}
