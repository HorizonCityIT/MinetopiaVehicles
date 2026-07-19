package nl.mtvehicles.core.infrastructure.modules;

import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.traffic.SirenManager;
import nl.mtvehicles.core.infrastructure.traffic.SpeedCameraManager;

/** Runtime module for speed enforcement and emergency vehicle sirens. */
public class TrafficModule {
    private static TrafficModule instance;
    private final SpeedCameraManager speedCameraManager = new SpeedCameraManager();
    private final SirenManager sirenManager = new SirenManager();

    public TrafficModule() {
        instance = this;
        Main.instance.registerListener(sirenManager);
        speedCameraManager.start();
        sirenManager.start();
    }

    public static TrafficModule getInstance() {
        return instance;
    }

    public SpeedCameraManager getSpeedCameraManager() {
        return speedCameraManager;
    }

    public void reload() {
        speedCameraManager.reload();
    }

    public void shutdown() {
        speedCameraManager.shutdown();
        sirenManager.shutdown();
    }
}
