package nl.mtvehicles.core.commands.vehiclesubs;

import nl.mtvehicles.core.infrastructure.models.MTVSubCommand;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;

/** Despawns only persisted vehicles that are actually present in a loaded world. */
public final class VehicleDespawnAll extends MTVSubCommand {
    @Override
    public boolean execute() {
        if (!checkPermission("mtvehicles.despawnall")) return true;

        VehicleUtils.DespawnAllResult result = VehicleUtils.despawnAllPersistedSpawnedVehicles();
        if (result.vehicles() == 0) {
            sendMessage("&eNessun veicolo realmente spawnato. &7I " + result.notSpawnedRecords()
                    + " record non presenti in gioco sono stati ignorati.");
            return true;
        }

        sendMessage("&aDespawn completato: &f" + result.vehicles() + " veicoli &7(" + result.entities()
                + " entità rimosse). &7Record non spawnati ignorati: &f" + result.notSpawnedRecords() + "&7.");
        return true;
    }
}
