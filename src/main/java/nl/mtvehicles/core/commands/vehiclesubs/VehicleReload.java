package nl.mtvehicles.core.commands.vehiclesubs;

import nl.mtvehicles.core.infrastructure.enums.Message;
import nl.mtvehicles.core.infrastructure.models.MTVSubCommand;
import nl.mtvehicles.core.infrastructure.modules.ConfigModule;
import nl.mtvehicles.core.infrastructure.modules.TrafficModule;
import org.bukkit.Bukkit;

/**
 * <b>/vehicle reload</b> - reload the plugin's configuration files.
 */
public class VehicleReload extends MTVSubCommand {
    public VehicleReload() {
        this.setPlayerCommand(false);
    }

    @Override
    public boolean execute() {
        if (!checkPermission("mtvehicles.reload")) return true;

        Bukkit.getLogger().info("Reload config files..");
        ConfigModule.reloadConfigs();
        if (TrafficModule.getInstance() != null) TrafficModule.getInstance().reload();
        Bukkit.getLogger().info("Files loaded!");
        sendMessage(Message.RELOAD_SUCCESSFUL);

        return true;
    }
}
