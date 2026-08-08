package nl.mtvehicles.core.commands.vehiclesubs;

import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.enums.Message;
import nl.mtvehicles.core.infrastructure.models.MTVSubCommand;
import nl.mtvehicles.core.infrastructure.utils.TextUtils;
import nl.mtvehicles.core.infrastructure.vehicle.Vehicle;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Force an online player into a selected seat of the owner's vehicle. */
public class VehicleForceMount extends MTVSubCommand {
    public VehicleForceMount() {
        setPlayerCommand(true);
    }

    @Override
    public boolean execute() {
        if (!checkPermission("mtvehicles.forcemount")) return true;
        Vehicle vehicle = getVehicle();
        if (vehicle == null) return true;
        if (arguments.length < 2 || arguments.length > 3) {
            sendMessage("&cUsa /veicolo sali <giocatore> [sedile]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(arguments[1]);
        if (target == null) {
            sendMessage(Message.PLAYER_NOT_FOUND);
            return true;
        }

        boolean mounted;
        if (arguments.length == 2) {
            mounted = VehicleUtils.forceMountPlayer(vehicle.getLicensePlate(), target);
        } else {
            try {
                mounted = VehicleUtils.forceMountPlayer(vehicle.getLicensePlate(), target,
                        Integer.parseInt(arguments[2]));
            } catch (NumberFormatException exception) {
                sendMessage("&cIl numero del sedile non è valido.");
                return true;
            }
        }

        if (!mounted) {
            sendMessage("&cImpossibile far salire il giocatore: sedile occupato o veicolo non attivo.");
            return true;
        }

        Player commandPlayer = player;
        String licensePlate = vehicle.getLicensePlate();
        Bukkit.getScheduler().runTaskLater(Main.instance, () -> {
            boolean confirmed = licensePlate.equals(VehicleUtils.getLicensePlate(target.getVehicle()));
            if (confirmed) {
                commandPlayer.sendMessage(TextUtils.colorize("&a" + target.getName() + " è salito sul veicolo."));
                target.sendMessage(TextUtils.colorize("&6Sei stato fatto salire su un veicolo."));
            } else {
                commandPlayer.sendMessage(TextUtils.colorize("&cIl server non è riuscito a confermare la salita del giocatore."));
            }
        }, 6L);
        return true;
    }
}
