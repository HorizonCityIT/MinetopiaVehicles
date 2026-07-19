package nl.mtvehicles.core.commands.vehiclesubs;

import nl.mtvehicles.core.infrastructure.models.MTVSubCommand;
import nl.mtvehicles.core.infrastructure.modules.TrafficModule;
import nl.mtvehicles.core.infrastructure.traffic.SpeedCameraManager;
import nl.mtvehicles.core.infrastructure.traffic.SpeedCameraManager.SpeedCamera;

import java.util.Locale;

/** Administration of static speed cameras and the dynamic speed gun. */
public class VehicleSpeedCamera extends MTVSubCommand {
    @Override
    public boolean execute() {
        if (TrafficModule.getInstance() == null) {
            sendMessage("&cIl modulo traffico non è disponibile.");
            return true;
        }
        if (arguments.length < 2) return sendUsage();

        SpeedCameraManager manager = TrafficModule.getInstance().getSpeedCameraManager();
        String operation = arguments[1].toLowerCase(Locale.ROOT);
        switch (operation) {
            case "add":
            case "aggiungi":
                if (!checkPermission("mtvehicles.speedcamera.admin")) return true;
                if (!requirePlayer() || arguments.length < 3) return sendUsage();
                Double limit = positiveNumber(arguments[2], 1000.0D);
                Double radius = arguments.length >= 4 ? positiveNumber(arguments[3], 64.0D) : 6.0D;
                if (limit == null || radius == null) return true;
                SpeedCamera camera = manager.addCamera(player.getLocation(), limit, radius);
                sendMessage(String.format(Locale.US,
                        "&aAutovelox statico creato. &7ID: &f%s &8| &7Limite: &f%.1f km/h &8| &7Raggio: &f%.1f",
                        camera.getId(), camera.getLimitKmh(), camera.getRadius()));
                return true;

            case "remove":
            case "rimuovi":
                if (!checkPermission("mtvehicles.speedcamera.admin")) return true;
                if (arguments.length >= 3) {
                    if (manager.removeById(arguments[2])) sendMessage("&aAutovelox rimosso.");
                    else sendMessage("&cNessun autovelox trovato con ID " + arguments[2] + ".");
                    return true;
                }
                if (!requirePlayer()) return true;
                SpeedCamera removed = manager.removeNearest(player.getLocation(), 10.0D);
                if (removed == null) sendMessage("&cNessun autovelox entro 10 blocchi.");
                else sendMessage("&aAutovelox " + removed.getId() + " rimosso.");
                return true;

            case "list":
            case "lista":
                if (!checkPermission("mtvehicles.speedcamera.admin")) return true;
                sendMessage("&6Autovelox statici: &f" + manager.getCameras().size());
                for (SpeedCamera listed : manager.getCameras()) {
                    sendMessage(String.format(Locale.US,
                            "&7- &f%s &8| &7%s %.1f %.1f %.1f &8| &f%.1f km/h &8| &fr=%.1f",
                            listed.getId(), listed.getWorld(), listed.getX(), listed.getY(), listed.getZ(),
                            listed.getLimitKmh(), listed.getRadius()));
                }
                return true;

            case "dynamic":
            case "dinamico":
            case "telelaser":
                if (!checkPermission("mtvehicles.speedcamera.dynamic")) return true;
                if (!requirePlayer()) return true;
                Double dynamicLimit = arguments.length >= 3 ? positiveNumber(arguments[2], 1000.0D) : 50.0D;
                if (dynamicLimit == null) return true;
                boolean enabled = manager.toggleDynamic(player, dynamicLimit);
                sendMessage(enabled
                        ? String.format(Locale.US, "&aTelelaser attivato &7(limite %.1f km/h). Guarda un veicolo.", dynamicLimit)
                        : "&cTelelaser disattivato.");
                return true;

            default:
                return sendUsage();
        }
    }

    private boolean requirePlayer() {
        if (player != null) return true;
        sendMessage("&cQuesto utilizzo del comando è disponibile solo in gioco.");
        return false;
    }

    private Double positiveNumber(String value, double maximum) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0.0D || parsed > maximum) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            sendMessage("&cValore non valido: " + value + " (deve essere tra 0 e " + maximum + ").");
            return null;
        }
    }

    private boolean sendUsage() {
        sendMessage("&6Autovelox MTVehicles:");
        sendMessage("&e/vehicle autovelox add <limite-kmh> [raggio]");
        sendMessage("&e/vehicle autovelox remove [id]");
        sendMessage("&e/vehicle autovelox list");
        sendMessage("&e/vehicle autovelox dynamic [limite-kmh]");
        return true;
    }
}
