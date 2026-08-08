package nl.mtvehicles.core.commands.vehiclesubs;

import nl.mtvehicles.core.infrastructure.enums.Message;
import nl.mtvehicles.core.infrastructure.models.MTVSubCommand;
import nl.mtvehicles.core.infrastructure.utils.TextUtils;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Force a player out of the owner's current vehicle. */
public class VehicleForceDismount extends MTVSubCommand {
    public VehicleForceDismount() {
        setPlayerCommand(true);
    }

    @Override
    public boolean execute() {
        if (!checkPermission("mtvehicles.forcemount")) return true;
        Vehicle vehicle = getVehicle();
        if (vehicle == null) return true;
        if (arguments.length != 2) {
            sendMessage("&cUsa /veicolo scendi <giocatore>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(arguments[1]);
        if (target == null) {
            sendMessage(Message.PLAYER_NOT_FOUND);
            return true;
        }
        if (!vehicle.getLicensePlate().equals(VehicleUtils.getLicensePlate(target.getVehicle()))
                || !VehicleUtils.forceDismountPlayer(target)) {
            sendMessage("&cIl giocatore non è seduto su questo veicolo.");
            return true;
        }

        sendMessage("&a" + target.getName() + " è sceso dal veicolo.");
        target.sendMessage(TextUtils.colorize("&6Sei stato fatto scendere dal veicolo."));
        return true;
    }
}
