package nl.mtvehicles.core;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorTest {
    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "mtvehicles.anwb",
            "mtvehicles.benzine",
            "mtvehicles.buycar",
            "mtvehicles.buyvoucher",
            "mtvehicles.delete",
            "mtvehicles.despawn",
            "mtvehicles.despawnall",
            "mtvehicles.edit",
            "mtvehicles.filljerrycans",
            "mtvehicles.forcemount",
            "mtvehicles.givecar",
            "mtvehicles.givefuel",
            "mtvehicles.givevoucher",
            "mtvehicles.kofferbak",
            "mtvehicles.language",
            "mtvehicles.menu",
            "mtvehicles.nolimit",
            "mtvehicles.oppakken",
            "mtvehicles.refill",
            "mtvehicles.reload",
            "mtvehicles.repair",
            "mtvehicles.restore",
            "mtvehicles.ride",
            "mtvehicles.setowner",
            "mtvehicles.speedcamera.admin",
            "mtvehicles.speedcamera.dynamic",
            "mtvehicles.update"
    );

    @Test
    void descriptorDeclaresEveryAdministrativePermission() {
        Map<String, Object> descriptor = loadDescriptor();
        Map<String, Object> permissions = map(descriptor.get("permissions"));
        assertNotNull(permissions);
        assertEquals(ADMIN_PERMISSIONS.size() + 1, permissions.size());
        assertTrue(permissions.containsKey("mtvehicles.admin"));
        assertTrue(permissions.keySet().containsAll(ADMIN_PERMISSIONS));

        Map<String, Object> admin = map(permissions.get("mtvehicles.admin"));
        Map<String, Object> children = map(admin.get("children"));
        assertNotNull(children);
        assertEquals(ADMIN_PERMISSIONS, children.keySet());
    }

    @Test
    void descriptorProvidesNativeItalianCommandAlias() {
        Map<String, Object> descriptor = loadDescriptor();
        Map<String, Object> commands = map(descriptor.get("commands"));
        Map<String, Object> command = map(commands.get("minetopiavehicles"));
        assertTrue(((List<?>) command.get("aliases")).contains("veicolo"));
    }

    private static Map<String, Object> loadDescriptor() {
        InputStream stream = PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);
        return new Yaml().load(stream);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
