package nl.mtvehicles.core.commands.vehiclesubs;

import nl.mtvehicles.core.infrastructure.enums.Message;
import nl.mtvehicles.core.infrastructure.models.MTVSubCommand;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** Add one player as both driver and passenger with a single persistence operation. */
public class VehicleAddUser extends MTVSubCommand {
    public VehicleAddUser() {
        setPlayerCommand(true);
    }

    @Override
    public boolean execute() {
        Vehicle vehicle = getVehicle();
        if (vehicle == null) return true;
        if (arguments.length != 2) {
            sendMessage("&cUsa /veicolo aggiungi <giocatore>");
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
        boolean changed = false;
        if (!riders.contains(uuid)) {
            riders.add(uuid);
            changed = true;
        }
        if (!members.contains(uuid)) {
            members.add(uuid);
            changed = true;
        }
        if (!changed) {
            sendMessage("&eIl giocatore ha già accesso completo a questo veicolo.");
            return true;
        }

        vehicle.setRiders(riders);
        vehicle.setMembers(members);
        vehicle.save();
        sendModificationSavedOnce(vehicle.getLicensePlate());
        return true;
    }
}
