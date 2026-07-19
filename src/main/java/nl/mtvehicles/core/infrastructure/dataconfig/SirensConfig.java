package nl.mtvehicles.core.infrastructure.dataconfig;

import nl.mtvehicles.core.infrastructure.enums.ConfigType;
import nl.mtvehicles.core.infrastructure.models.MTVConfig;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Configurable emergency vehicle siren sequences. */
public class SirensConfig extends MTVConfig {
    public SirensConfig() {
        super(ConfigType.SIRENS);
    }

    public boolean exists(String type) {
        if (type == null) return false;
        return existsDirect(type.trim().toUpperCase(Locale.ROOT));
    }

    public int getIntervalTicks(String type) {
        return Math.max(2, getConfiguration().getInt("sirens." + normalize(type) + ".intervalTicks", 8));
    }

    public float getVolume(String type) {
        return (float) Math.max(0.0D, getConfiguration().getDouble("sirens." + normalize(type) + ".volume", 2.0D));
    }

    public List<Map<?, ?>> getTones(String type) {
        List<Map<?, ?>> tones = getConfiguration().getMapList("sirens." + normalize(type) + ".tones");
        return tones == null ? Collections.emptyList() : tones;
    }

    public String normalize(String type) {
        String normalized = type == null ? "POLICE" : type.trim().toUpperCase(Locale.ROOT);
        return existsDirect(normalized) ? normalized : "POLICE";
    }

    private boolean existsDirect(String type) {
        return getConfiguration().isConfigurationSection("sirens." + type);
    }
}
