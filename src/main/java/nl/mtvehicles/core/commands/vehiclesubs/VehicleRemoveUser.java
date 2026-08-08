package nl.mtvehicles.core.commands.vehiclesubs;

import nl.mtvehicles.core.infrastructure.enums.Message;
import nl.mtvehicles.core.infrastructure.models.MTVSubCommand;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** Remove one player's driver and passenger access with a single persistence operation. */
public class VehicleRemoveUser extends MTVSubCommand {
    public VehicleRemoveUser() {
        setPlayerCommand(true);
    }

    @Override
    public boolean execute() {
        Vehicle vehicle = getVehicle();
        if (vehicle == null) return true;
        if (arguments.length != 2) {
            sendMessage("&cUsa /veicolo rimuovi <giocatore>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(arguments[1]);
        if (target == null) {
            sendMessage(Message.PLAYER_NOT_FOUND);
            return true;
        }

        String uuid = target.getUniqueId().toString();
        List<String> riders = vehicle.getRiders();
        List<String> members = vehicle.getMembers();
        boolean changed = riders.removeIf(uuid::equals) | members.removeIf(uuid::equals);
        if (!changed) {
            sendMessage("&eIl giocatore non ha accesso a questo veicolo.");
            return true;
        }

        vehicle.setRiders(riders);
        vehicle.setMembers(members);
        vehicle.save();
        sendModificationSavedOnce(vehicle.getLicensePlate());
        return true;
    }
}
