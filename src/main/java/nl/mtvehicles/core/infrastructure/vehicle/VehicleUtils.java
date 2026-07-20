package nl.mtvehicles.core.infrastructure.vehicle;

import de.tr7zw.changeme.nbtapi.NBTItem;
import nl.mtvehicles.core.Main;
import nl.mtvehicles.core.infrastructure.annotations.ToDo;
import nl.mtvehicles.core.infrastructure.dataconfig.DefaultConfig;
import nl.mtvehicles.core.infrastructure.dataconfig.VehicleDataConfig;
import nl.mtvehicles.core.infrastructure.enums.InventoryTitle;
import nl.mtvehicles.core.infrastructure.enums.Message;
import nl.mtvehicles.core.infrastructure.enums.RegionAction;
import nl.mtvehicles.core.infrastructure.enums.VehicleType;
import nl.mtvehicles.core.infrastructure.modules.ConfigModule;
import nl.mtvehicles.core.infrastructure.utils.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/**
 * Useful methods for vehicles
 * @see Vehicle
 */
public final class VehicleUtils {

    private static final String ATTACHED_VISUAL_PREFIX = "MTVATTACHMENT_";
    private static final Map<String, AttachedVehicleVisual> attachedVehicleVisuals = new HashMap<>();

    /**
     * A private constructor - makes this a "static class"
     */
    private VehicleUtils(){}

    /**
     * Register a policy that can prevent a player from driving a vehicle.
     * Every registered policy must allow the action. Entry as a passenger or
     * driver is handled separately and is not cancelled by this policy.
     *
     * @param key unique owner key, normally namespaced with the plugin name
     * @param policy receives license plate and driver; return false to freeze the vehicle
     */
    public static void registerDrivingPolicy(String key, BiPredicate<String, Player> policy) {
        VehicleAccessPolicies.registerDrivingPolicy(key, policy);
    }

    /**
     * Register an override that lets a player enter a normally private vehicle.
     * Region restrictions and seat occupancy checks still apply.
     */
    public static void registerEntryOverride(String key, BiPredicate<String, Player> override) {
        VehicleAccessPolicies.registerEntryOverride(key, override);
    }

    /** Remove all driving and entry policies registered with the supplied key. */
    public static void unregisterAccessPolicy(String key) {
        VehicleAccessPolicies.unregister(key);
    }

    /** Check all policies registered by external plugins for the current driver. */
    public static boolean isDrivingAllowed(String licensePlate, Player player) {
        return VehicleAccessPolicies.isDrivingAllowed(licensePlate, player);
    }

    /** Check whether an external plugin explicitly allows entry to this vehicle. */
    public static boolean hasEntryOverride(String licensePlate, Player player) {
        return VehicleAccessPolicies.hasEntryOverride(licensePlate, player);
    }

    /** Clears external callbacks during plugin shutdown or reload. */
    public static void clearAccessPolicies() {
        VehicleAccessPolicies.clear();
    }

    /**
     * HashMap containing information about which trunk a player has opened (determined by vehicle's license plate)
     * @see VehicleData#getTrunkViewers(String) 
     */
    public static HashMap<Player, String> openedTrunk = new HashMap<>();

    /**
     * Spawn a vehicle
     * @param licensePlate Vehicle's license plate
     * @param location Location where the vehicle should be spawned
     *
     * @throws IllegalArgumentException If vehicle with given license plate does not exist
     */
    public static void spawnVehicle(String licensePlate, Location location) throws IllegalArgumentException {
        if (!existsByLicensePlate(licensePlate)) throw new IllegalArgumentException("Vehicle does not exists.");

        Vehicle vehicle = getVehicle(licensePlate);
        if (vehicle == null) throw new IllegalArgumentException("Vehicle configuration could not be resolved.");
        List<Map<String, Double>> seats = vehicle.getSeats();
        if (seats.isEmpty()) throw new IllegalArgumentException("Vehicle has no configured driver seat.");
        Map<String, Double> mainSeat = seats.getFirst();
        Location locationMainSeat = location.clone().add(mainSeat.get("x"), mainSeat.get("y"), mainSeat.get("z"));

        ItemStack skinItem = getVehicleSkinItem(licensePlate);
        if (skinItem == null) {
            throw new IllegalArgumentException("Could not resolve the configured vehicle skin.");
        }

        List<Entity> existingComponents = findVehicleEntities(licensePlate, null, null);
        boolean alreadySpawned = existingComponents.stream()
                .anyMatch(entity -> ("MTVEHICLES_MAINSEAT_" + licensePlate).equals(entity.getCustomName()));
        if (alreadySpawned) throw new IllegalArgumentException("Vehicle is already spawned.");
        if (!existingComponents.isEmpty()) {
            existingComponents.forEach(Entity::remove);
            VehicleData.clearRuntimeData(licensePlate, true);
            Main.logWarning("Removed " + existingComponents.size() + " incomplete entity component(s) before spawning "
                    + licensePlate + '.');
        }

        ArmorStand standSkin = location.getWorld().spawn(location, ArmorStand.class);
        allowTicking(standSkin);
        standSkin.setVisible(false);
        standSkin.setCustomName("MTVEHICLES_SKIN_" + licensePlate);
        standSkin.getEquipment().setHelmet(skinItem);

        ArmorStand standMain = location.getWorld().spawn(location, ArmorStand.class);
        standMain.setVisible(false);
        standMain.setCustomName("MTVEHICLES_MAIN_" + licensePlate);

        ArmorStand standMainSeat = locationMainSeat.getWorld().spawn(locationMainSeat, ArmorStand.class);
        standMainSeat.setCustomName("MTVEHICLES_MAINSEAT_" + licensePlate);
        standMainSeat.setGravity(false);
        standMainSeat.setVisible(false);

        VehicleData.autostand.put("MTVEHICLES_SKIN_" + licensePlate, standSkin);
        VehicleData.autostand.put("MTVEHICLES_MAIN_" + licensePlate, standMain);
        VehicleData.autostand.put("MTVEHICLES_MAINSEAT_" + licensePlate, standMainSeat);

        if (ConfigModule.vehicleDataConfig.getType(licensePlate).isBoat()){
            standMain.setGravity(false);
            standSkin.setGravity(false);
        }

        if (ConfigModule.vehicleDataConfig.getType(licensePlate).isHelicopter()) {
            Map<?, ?> blade = getRotorDefinition(vehicle);
            if (blade != null && storeRotorOffsets(licensePlate, blade)) {
                String bladeKey = "MTVEHICLES_WIEKENS_" + licensePlate;
                Location locationBlade = location.clone().add(
                        VehicleData.wiekenz.get(bladeKey),
                        VehicleData.wiekeny.get(bladeKey),
                        VehicleData.wiekenx.get(bladeKey));
                ArmorStand standRotors = locationBlade.getWorld().spawn(locationBlade, ArmorStand.class);
                standRotors.setCustomName(bladeKey);
                standRotors.setGravity(false);
                standRotors.setVisible(false);
                VehicleData.autostand.put(bladeKey, standRotors);

                if ((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.HELICOPTER_BLADES_ALWAYS_ON)) {
                    allowTicking(standRotors);
                    if (blade.get("item") instanceof ItemStack) {
                        standRotors.setHelmet((ItemStack) blade.get("item"));
                    }
                }
            }
        }
    }

    private static ItemStack getVehicleSkinItem(String licensePlate) {
        return ItemUtils.getVehicleItem(
                ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.SKIN_ITEM).toString(),
                (int) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.SKIN_DAMAGE),
                false,
                ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.NAME).toString(),
                licensePlate);
    }

    /**
     * Creates a non-interactive copy of a vehicle model and keeps it attached to another vehicle.
     * This is intended for integrations such as tow-truck flatbeds: the source vehicle remains a
     * normal MTVehicles record, while only its configured skin is rendered on the host vehicle.
     *
     * @param hostPlate vehicle that carries the visual
     * @param sourcePlate vehicle whose skin must be rendered
     * @param forwardOffset offset along the host's forward direction (negative is behind)
     * @param verticalOffset offset above the host's main armor stand
     * @param sidewaysOffset lateral offset, using MTVehicles' seat coordinate convention
     * @param yawOffset additional model rotation in degrees
     * @return {@code true} when the visual was created
     */
    public static boolean attachVehicleVisual(String hostPlate, String sourcePlate,
                                              double forwardOffset, double verticalOffset,
                                              double sidewaysOffset, float yawOffset) {
        return attachVehicleVisual(hostPlate, sourcePlate, forwardOffset, verticalOffset,
                sidewaysOffset, yawOffset, 1.0D);
    }

    /**
     * Creates a scaled, non-interactive copy of a vehicle model attached to another vehicle.
     * The scale is applied through Bukkit's entity scale attribute, available on Paper 1.21+.
     *
     * @param hostPlate vehicle that carries the visual
     * @param sourcePlate vehicle whose skin must be rendered
     * @param forwardOffset offset along the host's forward direction (negative is behind)
     * @param verticalOffset offset above the host's main armor stand
     * @param sidewaysOffset lateral offset, using MTVehicles' seat coordinate convention
     * @param yawOffset additional model rotation in degrees
     * @param scale visual scale; values are clamped between {@code 0.25} and {@code 2.0}
     * @return {@code true} when the visual was created
     */
    public static boolean attachVehicleVisual(String hostPlate, String sourcePlate,
                                              double forwardOffset, double verticalOffset,
                                              double sidewaysOffset, float yawOffset, double scale) {
        return attachVehicleVisual(hostPlate, sourcePlate, forwardOffset, verticalOffset,
                sidewaysOffset, yawOffset, scale, 0.0D);
    }

    /**
     * Creates a scaled and pitched vehicle visual. Negative pitch values raise the model's front,
     * allowing a loaded vehicle to follow a tow-truck ramp instead of remaining horizontal.
     *
     * @param hostPlate vehicle that carries the visual
     * @param sourcePlate vehicle whose skin must be rendered
     * @param forwardOffset offset along the host's forward direction (negative is behind)
     * @param verticalOffset offset above the host's main armor stand
     * @param sidewaysOffset lateral offset, using MTVehicles' seat coordinate convention
     * @param yawOffset additional horizontal model rotation in degrees
     * @param scale visual scale; values are clamped between {@code 0.25} and {@code 2.0}
     * @param pitchOffset vertical model inclination; values are clamped between {@code -45} and {@code 45}
     * @return {@code true} when the visual was created
     */
    public static boolean attachVehicleVisual(String hostPlate, String sourcePlate,
                                              double forwardOffset, double verticalOffset,
                                              double sidewaysOffset, float yawOffset, double scale,
                                              double pitchOffset) {
        if (!existsByLicensePlate(hostPlate) || !existsByLicensePlate(sourcePlate)) return false;

        String key = attachmentKey(hostPlate);
        AttachedVehicleVisual current = attachedVehicleVisuals.get(key);
        if (current != null && current.stand.isValid()) return false;
        attachedVehicleVisuals.remove(key);

        Location hostLocation = getLocation(hostPlate);
        if (hostLocation == null || hostLocation.getWorld() == null) return false;
        ItemStack skinItem = getVehicleSkinItem(sourcePlate);
        if (skinItem == null) return false;

        ArmorStand stand = hostLocation.getWorld().spawn(hostLocation, ArmorStand.class);
        allowTicking(stand);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setPersistent(false);
        stand.setSilent(true);
        stand.setCustomName(ATTACHED_VISUAL_PREFIX + hostPlate);
        stand.setCustomNameVisible(false);
        stand.getEquipment().setHelmet(skinItem);
        double safePitch = Double.isFinite(pitchOffset)
                ? Math.max(-45.0D, Math.min(45.0D, pitchOffset))
                : 0.0D;
        stand.setHeadPose(new EulerAngle(Math.toRadians(safePitch), 0.0D, 0.0D));

        double safeScale = Double.isFinite(scale)
                ? Math.max(0.25D, Math.min(2.0D, scale))
                : 1.0D;
        if (Math.abs(safeScale - 1.0D) > 0.0001D && !applyEntityScale(stand, safeScale)) {
            stand.remove();
            return false;
        }

        AttachedVehicleVisual visual = new AttachedVehicleVisual(
                hostPlate, sourcePlate, stand, forwardOffset, verticalOffset, sidewaysOffset, yawOffset);
        attachedVehicleVisuals.put(key, visual);
        updateAttachedVehicleVisual(hostPlate);
        return true;
    }

    private static boolean applyEntityScale(ArmorStand stand, double scale) {
        AttributeInstance attribute = stand.getAttribute(Attribute.SCALE);
        if (attribute == null) return false;
        attribute.setBaseValue(scale);
        return true;
    }

    /** Removes the visual attached to the given host vehicle, if present. */
    public static boolean removeAttachedVehicleVisual(String hostPlate) {
        AttachedVehicleVisual visual = attachedVehicleVisuals.remove(attachmentKey(hostPlate));
        if (visual == null) return false;
        if (visual.stand.isValid()) visual.stand.remove();
        return true;
    }

    /** Returns whether a valid visual is currently attached to the host vehicle. */
    public static boolean hasAttachedVehicleVisual(String hostPlate) {
        AttachedVehicleVisual visual = attachedVehicleVisuals.get(attachmentKey(hostPlate));
        if (visual == null) return false;
        if (visual.stand.isValid()) return true;
        attachedVehicleVisuals.remove(attachmentKey(hostPlate));
        return false;
    }

    /** Repositions an attached visual. Called by the movement engine after its host moves. */
    public static void updateAttachedVehicleVisual(String hostPlate) {
        String key = attachmentKey(hostPlate);
        AttachedVehicleVisual visual = attachedVehicleVisuals.get(key);
        if (visual == null) return;
        if (!visual.stand.isValid()) {
            attachedVehicleVisuals.remove(key);
            return;
        }

        Location host = getLocation(visual.hostPlate);
        if (host == null || host.getWorld() == null) return;
        Location forward = host.clone().add(host.getDirection().setY(0).normalize().multiply(visual.forwardOffset));
        double yawRadians = Math.toRadians(forward.getYaw());
        double x = forward.getX() + visual.sidewaysOffset * Math.cos(yawRadians);
        double z = forward.getZ() + visual.sidewaysOffset * Math.sin(yawRadians);
        Location destination = new Location(host.getWorld(), x, host.getY() + visual.verticalOffset, z,
                host.getYaw() + visual.yawOffset, host.getPitch());
        visual.stand.teleport(destination);
    }

    /** Removes every transient integration visual, used during plugin shutdown/reload. */
    public static void removeAllAttachedVehicleVisuals() {
        for (AttachedVehicleVisual visual : new ArrayList<>(attachedVehicleVisuals.values())) {
            if (visual.stand.isValid()) visual.stand.remove();
        }
        attachedVehicleVisuals.clear();
    }

    private static String attachmentKey(String hostPlate) {
        return hostPlate.toUpperCase(Locale.ROOT);
    }

    private static final class AttachedVehicleVisual {
        private final String hostPlate;
        @SuppressWarnings("unused")
        private final String sourcePlate;
        private final ArmorStand stand;
        private final double forwardOffset;
        private final double verticalOffset;
        private final double sidewaysOffset;
        private final float yawOffset;

        private AttachedVehicleVisual(String hostPlate, String sourcePlate, ArmorStand stand,
                                      double forwardOffset, double verticalOffset,
                                      double sidewaysOffset, float yawOffset) {
            this.hostPlate = hostPlate;
            this.sourcePlate = sourcePlate;
            this.stand = stand;
            this.forwardOffset = forwardOffset;
            this.verticalOffset = verticalOffset;
            this.sidewaysOffset = sidewaysOffset;
            this.yawOffset = yawOffset;
        }
    }

    /**
     * Get license plate from a vehicle item
     * @param item Vehicle as Item
     * @return Vehicle's license plate
     */
    public static String getLicensePlate(ItemStack item){
        NBTItem nbt = new NBTItem(item);
        return nbt.getString("mtvehicles.kenteken");
    }

    /**
     * Get Vehicle instance from a vehicle item
     * @param item Vehicle as Item
     * @return Vehicle
     * @see #getLicensePlate(ItemStack)
     */
    public static Vehicle getVehicle(ItemStack item){
        return getVehicle(getLicensePlate(item));
    }

    /**
     * Get the license plate of player's driven vehicle
     * @param p Player
     * @return Returns null if no vehicle is being driven
     * @see #getDrivenVehicle(Player)
     */
    @Nullable
    public static String getDrivenVehiclePlate(Player p){
        if (p.getVehicle() == null) return null;
        if (!p.getVehicle().getCustomName().contains("MTVEHICLES_")) return null;

        String[] name = p.getVehicle().getCustomName().split("_");
        return name[2];
    }

    /**
     * Get the player's driven vehicle
     * @param p Player
     * @return Returns null if no vehicle is being driven
     * @see #getDrivenVehiclePlate(Player)
     */
    public static Vehicle getDrivenVehicle(Player p){
        if (getDrivenVehiclePlate(p) == null) return null;
        return getVehicle(getDrivenVehiclePlate(p));
    }

    /**
     * Check if given UUID exists (to prevent further issues)
     * @since 2.5.1
     */
    public static boolean vehicleUUIDExists(String uuid){
        boolean exists = false;
        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        outerLoop:
        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("uuid") != null) {
                    if (skin.get("uuid").equals(uuid)) {
                        exists = true;
                        break outerLoop;
                    }
                }
            }
        }
        return exists;
    }

    /**
     * Prepare a vehicle and its item without registering it. The database row
     * is created only after {@link PreparedVehicle#deliverTo(Player)} succeeds.
     * @param owner Vehicle's owner
     * @param uuid Vehicle's UUID (UUID may be found in vehicles.yml)
     * @return Null if the UUID is unknown; otherwise, a pending delivery
     */
    public static PreparedVehicle prepareVehicleByUUID(OfflinePlayer owner, String uuid) {
        if (owner == null || uuid == null) return null;
        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("uuid") != null) {
                    if (skin.get("uuid").equals(uuid)) {
                        String licensePlate = ConfigModule.vehicleDataConfig.reserveNextLicensePlate();
                        ItemStack item;
                        try {
                            item = ItemUtils.getVehicleItem(
                                    skin.get("SkinItem").toString(),
                                    (int) skin.get("itemDamage"),
                                    false,
                                    (String) skin.get("name"),
                                    licensePlate,
                                    "mtcustom",
                                    skin.get("nbtValue"));
                        } catch (RuntimeException exception) {
                            ConfigModule.vehicleDataConfig.releaseLicensePlateReservation(licensePlate);
                            throw exception;
                        }
                        if (item == null) {
                            ConfigModule.vehicleDataConfig.releaseLicensePlateReservation(licensePlate);
                            return null;
                        }

                        try {
                            Vehicle vehicle = new Vehicle(
                                    null,
                                    licensePlate,
                                    (String) skin.get("name"),
                                    VehicleType.valueOf((String) configVehicle.get("vehicleType")),
                                    false,
                                    (int) skin.get("itemDamage"),
                                    (String) skin.get("SkinItem"),
                                    false,
                                    (boolean) configVehicle.get("hornEnabled"),
                                    (double) configVehicle.get("maxHealth"),
                                    (boolean) configVehicle.get("benzineEnabled"),
                                    100,
                                    0.01,
                                    (boolean) configVehicle.get("kofferbakEnabled"),
                                    1,
                                    null,
                                    (double) configVehicle.get("acceleratieSpeed"),
                                    (double) configVehicle.get("maxSpeed"),
                                    (double) configVehicle.get("maxSpeedBackwards"),
                                    (double) configVehicle.get("brakingSpeed"),
                                    (double) configVehicle.get("aftrekkenSpeed"),
                                    (int) configVehicle.get("rotateSpeed"),
                                    owner.getUniqueId(),
                                    null,
                                    null,
                                    (double) skin.get("price"),
                                    skin.get("nbtValue") == null ? null : skin.get("nbtValue").toString()
                            );
                            return new PreparedVehicle(item, vehicle);
                        } catch (RuntimeException exception) {
                            ConfigModule.vehicleDataConfig.releaseLicensePlateReservation(licensePlate);
                            throw exception;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Register a new identity only after its item is verifiably present in the
     * owner's inventory. This prevents failed deliveries from creating rows.
     */
    public static boolean registerDeliveredVehicle(Player holder, ItemStack deliveredItem, Vehicle vehicle) {
        if (holder == null || deliveredItem == null || vehicle == null || vehicle.getOwnerUUID() == null) return false;
        String license = getLicensePlate(deliveredItem);
        if (license == null || license.isBlank() || !license.equals(vehicle.getLicensePlate())) return false;
        if (!holder.getUniqueId().equals(vehicle.getOwnerUUID()) || existsByLicensePlate(license)) return false;
        if (!hasVehicleItem(holder, license)) return false;

        try {
            vehicle.saveNew();
            return true;
        } catch (RuntimeException exception) {
            Main.instance.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not register delivered vehicle " + license, exception);
            return false;
        }
    }

    private static boolean hasVehicleItem(Player player, String license) {
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && license.equals(getLicensePlate(content))) return true;
        }
        return false;
    }

    private static void removeVehicleItem(Player player, String license) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack content = contents[slot];
            if (content != null && license.equals(getLicensePlate(content))) {
                player.getInventory().setItem(slot, null);
                return;
            }
        }
    }

    /** Deliver the item first and roll it back if identity registration fails. */
    public static boolean deliverNewVehicle(Player recipient, ItemStack item, Vehicle vehicle) {
        if (recipient == null || item == null || vehicle == null) {
            if (vehicle != null) ConfigModule.vehicleDataConfig.releaseLicensePlateReservation(vehicle.getLicensePlate());
            return false;
        }
        ItemStack delivered = item.clone();
        if (!recipient.getInventory().addItem(delivered).isEmpty()) {
            ConfigModule.vehicleDataConfig.releaseLicensePlateReservation(vehicle.getLicensePlate());
            return false;
        }
        if (registerDeliveredVehicle(recipient, delivered, vehicle)) return true;
        removeVehicleItem(recipient, vehicle.getLicensePlate());
        ConfigModule.vehicleDataConfig.releaseLicensePlateReservation(vehicle.getLicensePlate());
        return false;
    }

    public static final class PreparedVehicle {
        private final ItemStack item;
        private final Vehicle vehicle;
        private final AtomicBoolean completed = new AtomicBoolean();

        private PreparedVehicle(ItemStack item, Vehicle vehicle) {
            this.item = item;
            this.vehicle = vehicle;
        }

        public ItemStack item() {
            return item;
        }

        public Vehicle vehicle() {
            return vehicle;
        }

        public boolean deliverTo(Player recipient) {
            if (!completed.compareAndSet(false, true)) return false;
            return deliverNewVehicle(recipient, item, vehicle);
        }

        /** Release this pending identity when an integration decides not to deliver it. */
        public void cancel() {
            if (completed.compareAndSet(false, true)) {
                ConfigModule.vehicleDataConfig.releaseLicensePlateReservation(vehicle.getLicensePlate());
            }
        }
    }

    /**
     * Check whether horn is enabled on this vehicle.
     * @param damage The vehicle item's durability
     * @return True if horn is enabled
     */
    public static boolean getHornByDamage(int damage){
        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("itemDamage") != null) {
                    if (skin.get("itemDamage").equals(damage)) {
                        return (boolean) configVehicle.get("hornEnabled");
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check what is the max health of this vehicle.
     * @param damage The vehicle item's durability
     * @return Max health of the vehicle
     */
    public static double getMaxHealthByDamage(int damage){
        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("itemDamage") != null) {
                    if (skin.get("itemDamage").equals(damage)) {
                        return (double) configVehicle.get("maxHealth");
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Get a vehicle item by license plate. <b>Does not create a new vehicle.</b>
     * @param licensePlate Vehicle's license plate
     * @return The vehicle item - just aesthetic (null if license plate is not found)
     * @see #getItem(String)
     * @since 2.5.1
     */
    public static ItemStack getItemByLicensePlate(String licensePlate){
        return getItem(getUUID(licensePlate));
    }

    /**
     * Get a vehicle item by UUID. <b>Does not create a new vehicle - just for aesthetic purposes.</b>
     * @param carUUID Vehicle's UUID (UUID may be found in vehicles.yml)
     * @return The vehicle item - just aesthetic (null if UUID is not found)
     *
     */
    public static ItemStack getItem(String carUUID) {
        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        List<Map<?, ?>> matchedVehicles = new ArrayList<>();
        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("uuid") != null) {
                    if (skin.get("uuid").equals(carUUID)) {
                        if (skin.get("uuid") != null) {
                            ItemStack is = ItemUtils.getMenuVehicle(
                                    skin.get("SkinItem").toString(),
                                    (int) skin.get("itemDamage"),
                                    (String) skin.get("name"));
                            matchedVehicles.add(configVehicle);
                            return is;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check whether an entity is a vehicle
     * @param entity Checked entity
     * @return True if the entity is a vehicle
     */
    public static boolean isVehicle(Entity entity){
        return entity instanceof ArmorStand
                && entity.getCustomName() != null
                && entity.getCustomName().startsWith("MTVEHICLES_");
    }

    /**
     * Finds the closest spawned MTVehicles model around a location without exposing or deleting
     * unrelated armor stands. Distance is spherical even though Bukkit returns a bounding box.
     *
     * @param origin center of the search
     * @param radius maximum search radius, clamped between {@code 0.1} and {@code 32} blocks
     * @return closest license plate, or {@code null} when no spawned vehicle is in range
     */
    @Nullable
    public static String getNearestVehicleLicensePlate(Location origin, double radius) {
        if (origin == null || origin.getWorld() == null || !Double.isFinite(radius)
                || !Double.isFinite(origin.getX()) || !Double.isFinite(origin.getY())
                || !Double.isFinite(origin.getZ())) return null;
        double safeRadius = Math.max(0.1D, Math.min(32.0D, radius));
        double radiusSquared = safeRadius * safeRadius;
        String closestPlate = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : origin.getWorld().getNearbyEntities(
                origin, safeRadius, safeRadius, safeRadius)) {
            if (!isVehicle(entity)) continue;
            String plate = getLicensePlate(entity);
            if (plate == null || plate.isEmpty() || !existsByLicensePlate(plate)) continue;
            double distance = entity.getLocation().distanceSquared(origin);
            if (distance > radiusSquared) continue;
            if (distance < closestDistance
                    || (Math.abs(distance - closestDistance) < 0.0001D
                    && (closestPlate == null || plate.compareToIgnoreCase(closestPlate) < 0))) {
                closestPlate = plate;
                closestDistance = distance;
            }
        }
        return closestPlate;
    }

    /**
     * Get the current driver of the vehicle.
     * @param licensePlate Vehicle's license plate
     * @return Returns null if the vehicle is not being driven by any player at the moment.
     * @since 2.5.1
     */
    @Nullable
    public static Player getCurrentDriver(String licensePlate){
        ArmorStand driverSeat = VehicleData.autostand2.get(licensePlate);
        if (driverSeat == null || !driverSeat.isValid()) return null;
        return driverSeat.getPassenger() instanceof Player ? (Player) driverSeat.getPassenger() : null;
    }

    /**
     * Get license plate of an entity (which should be a vehicle - see {@link #isVehicle(Entity)}.
     * @param entity Vehicle's main armor stand
     * @return Vehicle's license plate
     */
    public static String getLicensePlate(@Nullable Entity entity){
        if (entity == null) return null;
        final String name = entity.getCustomName();
        if (name == null) return null;
        String[] parts = name.split("_", 3);
        return parts.length == 3 ? parts[2] : null;
    }

    /**
     * Get the UUID of a car by its license plate
     * @param licensePlate Vehicle's license plate
     * @return Vehicle's UUID
     */
    public static String getUUID(String licensePlate) {
        if (!existsByLicensePlate(licensePlate)) return null;

        Object skinItem = ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.SKIN_ITEM);
        Object skinDamage = ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.SKIN_DAMAGE);
        Object nbtValue = ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.NBT_VALUE);
        
        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("itemDamage").equals(skinDamage)) {
                    if (skin.get("SkinItem").equals(skinItem)) {
                        if (skin.get("nbtValue") != null) {
                            if (skin.get("nbtValue").equals(nbtValue)) {
                                return skin.get("uuid").toString();
                            }
                        } else {
                            return skin.get("uuid").toString();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get the Vehicle instance by a vehicle's license plate
     * @param licensePlate Vehicle's license plate
     * @return Vehicle instance
     *
     * @see Vehicle
     */
    @ToDo("Beautify the code inside this method.")
    public static Vehicle getVehicle(String licensePlate) {
        if (!existsByLicensePlate(licensePlate)) return null;
        
        Map<String, Object> vehicleData = new HashMap<>();
        for (VehicleDataConfig.Option option : VehicleDataConfig.Option.values()) {
            Object value = ConfigModule.vehicleDataConfig.get(licensePlate, option);
            if (value != null) {
                vehicleData.put(option.getPath(), value);
            }
        }

        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        List<Map<?, ?>> matchedVehicles = new ArrayList<>();
        double price = 0.0;

        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("itemDamage").equals(vehicleData.get(VehicleDataConfig.Option.SKIN_DAMAGE.getPath()))) {
                    if (skin.get("SkinItem").equals(vehicleData.get(VehicleDataConfig.Option.SKIN_ITEM.getPath()))) {
                        if (skin.get("nbtValue") != null) {
                            if (skin.get("nbtValue").equals(vehicleData.get(VehicleDataConfig.Option.NBT_VALUE.getPath()))) {
                                matchedVehicles.add(mergeVehicleDefinition(configVehicle, skin));
                                price = (double) skin.get("price");
                            }
                        } else {
                            matchedVehicles.add(mergeVehicleDefinition(configVehicle, skin));
                            price = (double) skin.get("price");
                        }
                    }
                }
            }
        }

        if (matchedVehicles.isEmpty()) return null;
        if (matchedVehicles.size() > 1) return null;

        return new Vehicle(
                matchedVehicles.get(0),
                licensePlate,
                (String) vehicleData.get(VehicleDataConfig.Option.NAME.getPath()),
                VehicleType.valueOf((String) vehicleData.get(VehicleDataConfig.Option.VEHICLE_TYPE.getPath())),
                (boolean) vehicleData.get(VehicleDataConfig.Option.IS_OPEN.getPath()),
                (int) vehicleData.get(VehicleDataConfig.Option.SKIN_DAMAGE.getPath()),
                (String) vehicleData.get(VehicleDataConfig.Option.SKIN_ITEM.getPath()),
                (boolean) vehicleData.get(VehicleDataConfig.Option.IS_GLOWING.getPath()),
                ConfigModule.vehicleDataConfig.isHornSet(licensePlate) ? (boolean) vehicleData.get(VehicleDataConfig.Option.HORN_ENABLED.getPath()) : ConfigModule.vehicleDataConfig.isHornEnabled(licensePlate),
                ConfigModule.vehicleDataConfig.isHealthSet(licensePlate) ? (double) vehicleData.get(VehicleDataConfig.Option.HEALTH.getPath()) : ConfigModule.vehicleDataConfig.getHealth(licensePlate),
                (boolean) vehicleData.get(VehicleDataConfig.Option.FUEL_ENABLED.getPath()),
                (double) vehicleData.get(VehicleDataConfig.Option.FUEL.getPath()),
                (double) vehicleData.get(VehicleDataConfig.Option.FUEL_USAGE.getPath()),
                (boolean) vehicleData.get(VehicleDataConfig.Option.TRUNK_ENABLED.getPath()),
                (int) vehicleData.get(VehicleDataConfig.Option.TRUNK_ROWS.getPath()),
                ConfigModule.vehicleDataConfig.getTrunkData(licensePlate),
                (double) vehicleData.get(VehicleDataConfig.Option.ACCELERATION_SPEED.getPath()),
                (double) vehicleData.get(VehicleDataConfig.Option.MAX_SPEED.getPath()),
                (double) vehicleData.get(VehicleDataConfig.Option.MAX_SPEED_BACKWARDS.getPath()),
                (double) vehicleData.get(VehicleDataConfig.Option.BRAKING_SPEED.getPath()),
                (double) vehicleData.get(VehicleDataConfig.Option.FRICTION_SPEED.getPath()),
                (int) vehicleData.get(VehicleDataConfig.Option.ROTATION_SPEED.getPath()),
                UUID.fromString((String) vehicleData.get(VehicleDataConfig.Option.OWNER.getPath())),
                ConfigModule.vehicleDataConfig.getRiders(licensePlate),
                ConfigModule.vehicleDataConfig.getMembers(licensePlate),
                price,
                (String) vehicleData.get(VehicleDataConfig.Option.NBT_VALUE.getPath())
        );
    }

    /** Skin-level FDO options override the parent vehicle definition when present. */
    private static Map<?, ?> mergeVehicleDefinition(Map<?, ?> vehicleDefinition, Map<?, ?> skinDefinition) {
        Map<Object, Object> merged = new LinkedHashMap<>();
        merged.putAll(vehicleDefinition);
        if (skinDefinition.containsKey("fdo")) merged.put("fdo", skinDefinition.get("fdo"));
        if (skinDefinition.containsKey("sirenType")) merged.put("sirenType", skinDefinition.get("sirenType"));
        return merged;
    }


    /**
     * Check whether this vehicle exists in the database (vehicleData.yml)
     * @param licensePlate Vehicle's license plate
     * @return True if vehicle is in the database (vehicleData.yml)
     */
    public static boolean existsByLicensePlate(String licensePlate) {
        return ConfigModule.vehicleDataConfig.containsLicensePlate(licensePlate);
    }

    /**
     * Check whether a player can ride/drive the vehicle.
     * @param player Player
     * @param licensePlate Vehicle's license plate
     * @return True if player is the vehicle's set rider.
     */
    public static boolean canRide(Player player, String licensePlate) {
        return ConfigModule.vehicleDataConfig.getRiders(licensePlate).contains(player.getUniqueId().toString());
    }

    /**
     * Check whether a player can sit in the vehicle.
     * @param player Player
     * @param licensePlate Vehicle's license plate
     * @return True if player is the vehicle's set passenger/member.
     */
    public static boolean canSit(Player player, String licensePlate) {
        return ConfigModule.vehicleDataConfig.getMembers(licensePlate).contains(player.getUniqueId().toString());
    }

    /**
     * Get the UUID of the vehicle's owner
     * @param licensePlate Vehicle's license plate
     * @return UUID of vehicle's owner
     */
    public static UUID getOwnerUUID(String licensePlate) {
        Object owner = ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.OWNER);
        if(owner == null) {
            return null;
        }
        return UUID.fromString(owner.toString());
    }

    /**
     * Open a vehicle's trunk to a player
     * @param p Player who is opening the trunk
     * @param license Vehicle's license plate
     */
    public static void openTrunk(Player p, String license) {
        if ((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.TRUNK_ENABLED)) {
            if (VehicleUtils.getVehicle(license) == null) {
                ConfigModule.messagesConfig.sendMessage(p, Message.VEHICLE_NOT_FOUND);
                return;
            }

            if (VehicleUtils.getVehicle(license).isOwner(p) || p.hasPermission("mtvehicles.kofferbak")) {
                Inventory inv = Bukkit.createInventory(null, (int) ConfigModule.vehicleDataConfig.get(license, VehicleDataConfig.Option.TRUNK_ROWS) * 9, InventoryTitle.VEHICLE_TRUNK.getStringTitle());

                if (ConfigModule.vehicleDataConfig.get(license, VehicleDataConfig.Option.TRUNK_DATA) != null) {
                    List<ItemStack> chestContentsFromConfig = (List<ItemStack>) ConfigModule.vehicleDataConfig.get(license, VehicleDataConfig.Option.TRUNK_DATA);

                    for (ItemStack item : chestContentsFromConfig) {
                        if (item != null) inv.addItem(item);
                    }
                }

                openedTrunk.put(p, license);
                VehicleData.trunkViewerAdd(license, p);
                p.openInventory(inv);

            } else {
                p.sendMessage(TextUtils.colorize(ConfigModule.messagesConfig.getMessage(Message.VEHICLE_NO_RIDER_TRUNK).replace("%p%", VehicleUtils.getVehicle(license).getOwnerName())));
            }
        }
    }

    /**
     * Check if trunk of a vehicle is opened by a specified player
     * @param p Player
     * @param license Vehicle's license plate
     * @since 2.5.1
     */
    public static boolean isTrunkInventoryOpen(Player p, String license) {
        return openedTrunk.containsKey(p) && openedTrunk.get(p).equals(license);
    }

    /**
     * Check whether a player is inside a vehicle
     * @param p Player
     * @return True if player is inside any MTV vehicle
     */
    public static boolean isInsideVehicle(Player p){
        if (p == null) return false;
        if (!p.isInsideVehicle()) return false;
        return VehicleUtils.isVehicle(p.getVehicle());
    }

    /**
     * Check whether a vehicle is occupied
     * @param licensePlate Vehicle's license plate
     * @return True if the vehicle is occupied
     */
    public static boolean isOccupied(String licensePlate) {
        return getCurrentDriver(licensePlate) != null;
    }

    /**
     * Get all the vehicle's set drivers/riders.
     * @param licensePlate Vehicle's license plate
     * @return String of all the drivers/riders separated by commas
     *
     * @deprecated Use {@link #canRide(Player, String)} instead.
     */
    @Deprecated
    public static String getRidersAsString(String licensePlate) {
        StringBuilder sb = new StringBuilder();
        for (String s : ConfigModule.vehicleDataConfig.getRiders(licensePlate)) {
            if (!UUID.fromString(s).equals(getOwnerUUID(licensePlate))) {
                sb.append(Bukkit.getOfflinePlayer(UUID.fromString(s)).getName()).append(", ");
            }
        }
        if (sb.toString().isEmpty()) {
            sb.append("Niemand");
        }
        return sb.toString();
    }

    /**
     * Pick up a vehicle and put it to player's inventory
     * @param license Vehicle's license plate
     * @param player Player
     */
    public static void pickupVehicle(String license, Player player) {
        pickupVehicle(license, player, null);
    }

    /**
     * Pick up a vehicle using a clicked component as a local lookup anchor.
     * This avoids scanning every loaded entity during normal player interaction.
     */
    public static void pickupVehicle(String license, Player player, @Nullable Entity anchor) {
        Vehicle vehicle = getVehicle(license);
        List<Entity> components = findVehicleEntities(license, anchor, anchor == null ? null : anchor.getWorld());
        if (vehicle == null) {
            components.forEach(Entity::remove);
            ConfigModule.messagesConfig.sendMessage(player, Message.VEHICLE_NOT_FOUND);
            return;
        }

        if (vehicle.getOwnerName() == null) {
            ConfigModule.messagesConfig.sendMessage(player, Message.VEHICLE_NOT_FOUND);
            Main.logSevere("Could not find the owner of the vehicle " + license + "! The vehicleData.yml must be malformed!");
            return;
        }

        if (vehicle.isOwner(player) && !((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.CAR_PICKUP)) || player.hasPermission("mtvehicles.oppakken")) {
            ArmorStand skin = components.stream()
                    .filter(entity -> ("MTVEHICLES_SKIN_" + license).equals(entity.getCustomName()))
                    .map(entity -> (ArmorStand) entity)
                    .findFirst()
                    .orElse(null);
            if (skin == null) {
                ConfigModule.messagesConfig.sendMessage(player, Message.VEHICLE_NOT_FOUND);
                return;
            }

            if (TextUtils.checkInvFull(player)) {
                ConfigModule.messagesConfig.sendMessage(player, Message.INVENTORY_FULL);
                return;
            }

            for (Player trunkViewer : VehicleData.getTrunkViewers(license)) {
                trunkViewer.closeInventory();
            }
            player.getInventory().addItem(skin.getHelmet());
            player.sendMessage(TextUtils.colorize(ConfigModule.messagesConfig.getMessage(Message.VEHICLE_PICKUP).replace("%p%", vehicle.getOwnerName())));
            removeAttachedVehicleVisual(license);
            components.forEach(Entity::remove);
            VehicleData.clearRuntimeData(license, true);
        } else {
            if ((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.CAR_PICKUP)) {
                player.sendMessage(TextUtils.colorize(ConfigModule.messagesConfig.getMessage(Message.CANNOT_DO_THAT_HERE)));
                return;
            }
            player.sendMessage(TextUtils.colorize(ConfigModule.messagesConfig.getMessage(Message.VEHICLE_NO_OWNER_PICKUP).replace("%p%", vehicle.getOwnerName())));
            return;
        }
    }

    private static boolean belongsToVehicle(Entity entity, String license) {
        String name = entity.getCustomName();
        return entity instanceof ArmorStand
                && name != null
                && name.startsWith("MTVEHICLES_")
                && license.equals(getLicensePlate(entity));
    }

    private static List<Entity> findVehicleEntities(String license, @Nullable Entity anchor, @Nullable World preferredWorld) {
        Set<Entity> matches = new LinkedHashSet<>();
        if (anchor != null) {
            if (belongsToVehicle(anchor, license)) matches.add(anchor);
            for (Entity nearby : anchor.getNearbyEntities(16, 16, 16)) {
                if (belongsToVehicle(nearby, license)) matches.add(nearby);
            }
            return new ArrayList<>(matches);
        }

        Collection<World> worlds = preferredWorld == null
                ? Bukkit.getServer().getWorlds()
                : Collections.singleton(preferredWorld);
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (belongsToVehicle(entity, license)) matches.add(entity);
            }
        }
        return new ArrayList<>(matches);
    }

    @Nullable
    private static Map<?, ?> getRotorDefinition(Vehicle vehicle) {
        if (vehicle == null || vehicle.getVehicleData() == null) return null;
        Object definitions = vehicle.getVehicleData().get("wiekens");
        if (!(definitions instanceof List) || ((List<?>) definitions).isEmpty()) return null;
        Object firstDefinition = ((List<?>) definitions).get(0);
        return firstDefinition instanceof Map ? (Map<?, ?>) firstDefinition : null;
    }

    private static boolean storeRotorOffsets(String licensePlate, Map<?, ?> blade) {
        Object x = blade.get("x");
        Object y = blade.get("y");
        Object z = blade.get("z");
        if (!(x instanceof Number) || !(y instanceof Number) || !(z instanceof Number)) return false;

        String bladeKey = "MTVEHICLES_WIEKENS_" + licensePlate;
        VehicleData.wiekenx.put(bladeKey, ((Number) x).doubleValue());
        VehicleData.wiekeny.put(bladeKey, ((Number) y).doubleValue());
        VehicleData.wiekenz.put(bladeKey, ((Number) z).doubleValue());
        return true;
    }

    /** Reload missing rotor offsets from the vehicle definition. */
    public static boolean loadRotorOffsets(String licensePlate) {
        Vehicle vehicle = getVehicle(licensePlate);
        Map<?, ?> blade = getRotorDefinition(vehicle);
        return blade != null && storeRotorOffsets(licensePlate, blade);
    }

    /**
     * Delete a vehicle from the database and despawn it from all worlds
     * @param licensePlates Vehicle's license plate
     * @throws IllegalArgumentException Thrown if given license plate is invalid.
     * @throws IllegalStateException Thrown if vehicle is already deleted
     * @since 2.5.4
     */
    public static void deleteVehicle(String... licensePlates) throws IllegalArgumentException, IllegalStateException {
        for (String licensePlate : licensePlates) {
            if (!existsByLicensePlate(licensePlate)) throw new IllegalArgumentException("Vehicle " + licensePlate + " does not exist.");
            despawnVehicle(licensePlate);
            ConfigModule.vehicleDataConfig.delete(licensePlate);
        }
    }

    /**
     * Teleport a vehicle to a location
     * @param licensePlate Vehicle's license plate
     * @param location Location where the vehicle should be teleported
     * @throws IllegalArgumentException Thrown if given license plate is invalid.
     */
    public static void teleportVehicle(String licensePlate, Location location) throws IllegalArgumentException {
        if (!existsByLicensePlate(licensePlate)) throw new IllegalArgumentException("Vehicle does not exists.");

        for (World world : Bukkit.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (belongsToVehicle(entity, licensePlate)) {
                    entity.teleport(location);
                }
            }
        }
        updateAttachedVehicleVisual(licensePlate);
    }

    /**
     * Despawn a vehicle specified by its license plate from all worlds
     * @param licensePlates Vehicle's license plate
     * @throws IllegalArgumentException Thrown if given license plate is invalid.
     * @since 2.5.1
     * @see #despawnVehicle(World, String...)
     * @return Number of vehicles despawned
     */
    public static int despawnVehicle(String... licensePlates) throws IllegalArgumentException {
        int despawned = 0;
        for (String licensePlate : licensePlates) {
            if (!existsByLicensePlate(licensePlate)) throw new IllegalArgumentException("Vehicle " + licensePlate + " does not exist.");
            removeAttachedVehicleVisual(licensePlate);
            for (Player trunkViewer : VehicleData.getTrunkViewers(licensePlate)){
                trunkViewer.closeInventory();
            }

            for (World world : Bukkit.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (belongsToVehicle(entity, licensePlate)) {
                        entity.remove();
                        despawned++;
                    }
                }
            }
            VehicleData.clearRuntimeData(licensePlate, true);
        }
        return despawned;
    }

    /**
     *  Despawn a vehicle specified by its license plate from a specified world
     * @param world World where the vehicle is being removed
     * @param licensePlates Vehicle's license plate
     * @throws IllegalArgumentException Thrown if given license plate is invalid.
     * @since 2.5.1
     * @see #despawnVehicle(String...)
     * @return Number of vehicles despawned
     */
    public static int despawnVehicle(World world, String... licensePlates) throws IllegalArgumentException {
        int despawned = 0;
        for (String licensePlate : licensePlates) {
            if (!existsByLicensePlate(licensePlate)) throw new IllegalArgumentException("Vehicle " + licensePlate + " does not exist.");
            removeAttachedVehicleVisual(licensePlate);

            for (Player trunkViewer : VehicleData.getTrunkViewers(licensePlate)){
                trunkViewer.closeInventory();
            }

            for (Entity entity : world.getEntities()) {
                if (belongsToVehicle(entity, licensePlate)) {
                    entity.remove();
                    despawned++;
                }
            }
            VehicleData.clearRuntimeData(licensePlate, true);
        }
        return despawned;
    }

    /**
     * Removes every vehicle that both exists in persistent storage and has a
     * real MAIN entity in a loaded world. Stale historical records are ignored
     * and are never deleted from storage.
     */
    public static DespawnAllResult despawnAllPersistedSpawnedVehicles() {
        Set<String> persisted = ConfigModule.vehicleDataConfig.getVehicles().keySet();
        Map<String, List<Entity>> componentsByLicense = new HashMap<>();
        Set<String> spawned = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (!isVehicle(entity)) continue;
                String license = getLicensePlate(entity);
                if (license == null || !persisted.contains(license)) continue;

                componentsByLicense.computeIfAbsent(license, ignored -> new ArrayList<>()).add(entity);
                if (("MTVEHICLES_MAIN_" + license).equals(entity.getCustomName())) spawned.add(license);
            }
        }

        int removedEntities = 0;
        for (String license : spawned) {
            removeAttachedVehicleVisual(license);
            for (Player viewer : VehicleData.getTrunkViewers(license)) viewer.closeInventory();

            for (Entity component : componentsByLicense.getOrDefault(license, List.of())) {
                for (Entity passenger : component.getPassengers()) {
                    if (passenger instanceof Player player) BossBarUtils.removeBossBar(player, license);
                }
                component.remove();
                removedEntities++;
            }
            VehicleData.clearRuntimeData(license, true);
        }

        return new DespawnAllResult(spawned.size(), removedEntities, persisted.size() - spawned.size());
    }

    public record DespawnAllResult(int vehicles, int entities, int notSpawnedRecords) {}

    /**
     * Get a list of all spawned vehicles' license plates in all worlds.
     * @return May return list with duplicates - if the same vehicle is spawned multiple times (see {@link #getUniqueSpawnedVehiclePlates()}).
     * @since 2.5.1
     * @see #getAllSpawnedVehiclePlates(World)
     */
    public static List<String> getAllSpawnedVehiclePlates(){
        List<String> list = new ArrayList<>();

        for (World world : Bukkit.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getCustomName() != null) {
                    String name = entity.getCustomName();
                    if (name.startsWith("MTVEHICLES_MAIN_")) list.add(name.substring("MTVEHICLES_MAIN_".length()));
                }
            }
        }
        return list;
    }

    /**
     * Get a list of all spawned vehicles' license plates in a specified world.
     * @return May return list with duplicates - if the same vehicle is spawned multiple times (see {@link #getUniqueSpawnedVehiclePlates(World)}).
     * @since 2.5.1
     * @see #getAllSpawnedVehiclePlates()
     */
    public static List<String> getAllSpawnedVehiclePlates(World world){
        List<String> list = new ArrayList<>();

        for (Entity entity : world.getEntities()) {
            if (entity.getCustomName() != null) {
                String name = entity.getCustomName();
                if (name.startsWith("MTVEHICLES_MAIN_")) list.add(name.substring("MTVEHICLES_MAIN_".length()));
            }
        }
        return list;
    }

    /**
     * Get a list of all spawned vehicles' license plates in all worlds.
     * @return Returns HashSet with no duplicates (see {@link #getAllSpawnedVehiclePlates()}).
     * @since 2.5.1
     * @see #getUniqueSpawnedVehiclePlates(World)
     */
    public static Set<String> getUniqueSpawnedVehiclePlates(){
        return new HashSet<>(getAllSpawnedVehiclePlates());
    }

    /**
     * Get a list of all spawned vehicles' license plates in a specified worlds.
     * @return Returns HashSet with no duplicates (see {@link #getAllSpawnedVehiclePlates(World)}).
     * @since 2.5.1
     * @see #getUniqueSpawnedVehiclePlates()
     */
    public static Set<String> getUniqueSpawnedVehiclePlates(World world){
        return new HashSet<>(getAllSpawnedVehiclePlates(world));
    }

    /**
     * Set vehicle's current fuel level
     * @param licensePlate Vehicle's license plate
     * @param fuel Fuel level (0–100)
     * @return True if fuel level was set successfully
     */
    public static boolean setFuel(String licensePlate, Double fuel){
        if (!existsByLicensePlate(licensePlate)) return false;
        if (!(fuel <= 100) || !(fuel >= 0)) return false;
        VehicleData.fuel.put(licensePlate, fuel);
        ConfigModule.vehicleDataConfig.set(licensePlate, VehicleDataConfig.Option.FUEL, fuel);
        return true;
    }

    /**
     * Create {@link VehicleData} (necessary for driving to work), helicopter blades, and make player enter a vehicle.
     * @param licensePlate Vehicle's license plate
     * @param p Player who is entering the vehicle
     */
    @ToDo("Beautify the code inside this method.")
    public static void enterVehicle(String licensePlate, Player p) {
        enterVehicle(licensePlate, p, null);
    }

    /**
     * Activate a vehicle using a clicked component as a local lookup anchor.
     */
    public static void enterVehicle(String licensePlate, Player p, @Nullable Entity anchor) {
        ArmorStand activeDriverSeat = VehicleData.autostand2.get(licensePlate);
        if (activeDriverSeat != null && !activeDriverSeat.isEmpty()) return;

        Vehicle vehicle = getVehicle(licensePlate);

        if (vehicle == null) {
            ConfigModule.messagesConfig.sendMessage(p, Message.VEHICLE_NOT_FOUND);
            return;
        }

        if (vehicle.getOwnerName() == null) {
            ConfigModule.messagesConfig.sendMessage(p, Message.VEHICLE_NOT_FOUND);
            Main.logSevere("Could not find the owner of vehicle " + licensePlate + "! The vehicleData.yml must be malformed!");
            return;
        }

        if (!hasEntryOverride(licensePlate, p) && !vehicle.isPublic() && !vehicle.isOwner(p)
                && !vehicle.canRide(p) && !p.hasPermission("mtvehicles.ride")){
            p.sendMessage(ConfigModule.messagesConfig.getMessage(Message.VEHICLE_NO_RIDER_ENTER).replace("%p%", vehicle.getOwnerName()));
            return;
        }

        List<Entity> components = findVehicleEntities(licensePlate, anchor, p.getWorld());
        ArmorStand skin = components.stream()
                .filter(entity -> ("MTVEHICLES_SKIN_" + licensePlate).equals(entity.getCustomName()))
                .map(entity -> (ArmorStand) entity)
                .findFirst()
                .orElse(null);
        if (skin == null) {
            ConfigModule.messagesConfig.sendMessage(p, Message.VEHICLE_NOT_FOUND);
            return;
        }

        boolean occupied = components.stream()
                .anyMatch(entity -> ("MTVEHICLES_MAINSEAT_" + licensePlate).equals(entity.getCustomName()) && !entity.isEmpty());
        if (occupied) return;

        Location location = skin.getLocation().clone();
        if (!ConfigModule.defaultConfig.canProceedWithAction(RegionAction.ENTER, vehicle.getVehicleType(), location, p)) {
            ConfigModule.messagesConfig.sendMessage(p, Message.CANNOT_DO_THAT_HERE);
            return;
        }

        vehicle.saveSeats();
        List<Map<String, Double>> seats = vehicle.getSeats();
        if (seats == null || seats.isEmpty()) {
            Main.logSevere("Vehicle " + licensePlate + " has no configured driver seat.");
            ConfigModule.messagesConfig.sendMessage(p, Message.VEHICLE_NOT_FOUND);
            return;
        }

        VehicleType vehicleType = vehicle.getVehicleType();
        VehicleData.fuel.put(licensePlate, vehicle.getFuel());
        VehicleData.fuelUsage.put(licensePlate, (double) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.FUEL_USAGE));
        VehicleData.type.put(licensePlate, vehicleType);
        VehicleData.setRotationSpeed(licensePlate, (int) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.ROTATION_SPEED));
        VehicleData.setSpeed(VehicleData.DataSpeed.MAXSPEED, licensePlate, (double) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.MAX_SPEED));
        VehicleData.setSpeed(VehicleData.DataSpeed.ACCELERATION, licensePlate, (double) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.ACCELERATION_SPEED));
        VehicleData.setSpeed(VehicleData.DataSpeed.BRAKING, licensePlate, (double) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.BRAKING_SPEED));
        VehicleData.setSpeed(VehicleData.DataSpeed.MAXSPEEDBACKWARDS, licensePlate, (double) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.MAX_SPEED_BACKWARDS));
        VehicleData.setSpeed(VehicleData.DataSpeed.FRICTION, licensePlate, (double) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.FRICTION_SPEED));

        basicStandCreator(licensePlate, "SKIN", location, skin.getHelmet(), false);
        basicStandCreator(licensePlate, "MAIN", location, null, true);
        VehicleData.seatsize.put(licensePlate, seats.size());

        for (int i = 1; i <= seats.size(); i++) {
            Map<String, Double> seat = seats.get(i - 1);
            if (i == 1) {
                mainSeatStandCreator(licensePlate, location, p, seat.get("x"), seat.get("y"), seat.get("z"));
                continue;
            }

            String seatKey = "MTVEHICLES_SEAT" + i + "_" + licensePlate;
            VehicleData.seatx.put(seatKey, seat.get("x"));
            VehicleData.seaty.put(seatKey, seat.get("y"));
            VehicleData.seatz.put(seatKey, seat.get("z"));
            Location seatLocation = location.clone().add(seat.get("x"), seat.get("y"), seat.get("z"));
            ArmorStand seatStand = seatLocation.getWorld().spawn(seatLocation, ArmorStand.class);
            allowTicking(seatStand);
            seatStand.setVisible(false);
            seatStand.setCustomName(seatKey);
            seatStand.setGravity(false);
            VehicleData.autostand.put(seatKey, seatStand);
        }

        if (vehicleType.isHelicopter()) {
            Map<?, ?> blade = getRotorDefinition(vehicle);
            if (blade != null && storeRotorOffsets(licensePlate, blade)) {
                String bladeKey = "MTVEHICLES_WIEKENS_" + licensePlate;
                double bladeX = VehicleData.wiekenx.get(bladeKey);
                double bladeY = VehicleData.wiekeny.get(bladeKey);
                double bladeZ = VehicleData.wiekenz.get(bladeKey);
                Location bladeLocation = location.clone().add(bladeZ, bladeY, bladeX);
                ArmorStand bladeStand = bladeLocation.getWorld().spawn(bladeLocation, ArmorStand.class);
                allowTicking(bladeStand);
                bladeStand.setVisible(false);
                bladeStand.setCustomName(bladeKey);
                bladeStand.setGravity(false);
                if (blade.get("item") instanceof ItemStack) {
                    bladeStand.setHelmet((ItemStack) blade.get("item"));
                }
                VehicleData.autostand.put(bladeKey, bladeStand);
            }
            VehicleData.maxheight.put(licensePlate, (int) ConfigModule.defaultConfig.get(DefaultConfig.Option.MAX_FLYING_HEIGHT));
        }

        BossBarUtils.addBossBar(p, licensePlate);
        p.sendMessage(TextUtils.colorize(ConfigModule.messagesConfig.getMessage(Message.VEHICLE_ENTER_RIDER).replace("%p%", vehicle.getOwnerName())));
        components.forEach(Entity::remove);
    }

    /**
     * Used in {@link #enterVehicle(String, Player)}.
     */
    private static void basicStandCreator(String license, String type, Location location, ItemStack item, Boolean gravity) {
        ArmorStand as = location.getWorld().spawn(location, ArmorStand.class);
        allowTicking(as);
        as.setVisible(false);
        as.setCustomName("MTVEHICLES_" + type + "_" + license);
        as.setHelmet(item);
        as.setGravity(gravity);

        VehicleData.autostand.put("MTVEHICLES_" + type + "_" + license, as);
    }

    private static void allowTicking(ArmorStand armorStand) {
        armorStand.setCanTick(true);
    }

    /**
     * Used in {@link #enterVehicle(String, Player)}.
     */
    private static void mainSeatStandCreator(String license, Location location, Player p, double x, double y, double z) {
        Location location2 = new Location(location.getWorld(), location.getX() + x, location.getY() + y, location.getZ() + z);
        ArmorStand as = location2.getWorld().spawn(location2, ArmorStand.class);
        allowTicking(as);
        as.setVisible(false);
        as.setCustomName("MTVEHICLES_MAINSEAT_" + license);
        as.setGravity(false);

        VehicleData.autostand.put("MTVEHICLES_MAINSEAT_" + license, as);
        VehicleData.speed.put(license, 0.0);
        VehicleData.mainx.put("MTVEHICLES_MAINSEAT_" + license, x);
        VehicleData.mainy.put("MTVEHICLES_MAINSEAT_" + license, y);
        VehicleData.mainz.put("MTVEHICLES_MAINSEAT_" + license, z);

        as.setPassenger(p);
        VehicleData.autostand2.put(license, as);
    }

    /**
     * Shortcut for {@link Vehicle.Seat#getSeat(Player)}
     */
    public static Vehicle.Seat getSeat(Player player){
        return Vehicle.Seat.getSeat(player);
    }

    /**
     * Kick a player out of a vehicle; if the player is a driver, {@link #turnOff(Vehicle)} is called as well.
     * @return True if successful
     * @throws IllegalStateException If player is not seated in a (valid) vehicle
     */
    public static boolean kickOut(Player player) throws IllegalStateException {
        if (getSeat(player) == null) throw new IllegalStateException("Player is not seated in a vehicle!");

        Entity seat = player.getVehicle();
        if (!getSeat(player).isDriver()) {
            return seat.removePassenger(player);
        }

        final String license = getLicensePlate(seat);
        if (seat.removePassenger(player)){
            BossBarUtils.removeBossBar(player, license);
            return turnOff(license);
        }
        return false;
    }

    /**
     * Get the location of a vehicle
     * @param vehicle Vehicle
     * @return Vehicle's location
     * @since 2.5.4
     * @see #getLocation(String)
     */
    public static Location getLocation(Vehicle vehicle){
        return getLocation(vehicle.getLicensePlate());
    }

    /**
     * Get the location of a vehicle
     * @param licensePlate Vehicle's license plate
     * @return Vehicle's location
     * @since 2.5.4
     */
    public static Location getLocation(String licensePlate){
        if (VehicleData.autostand.get("MTVEHICLES_MAIN_" + licensePlate) == null) return null;
        return VehicleData.autostand.get("MTVEHICLES_MAIN_" + licensePlate).getLocation();
    }

    /**
     * Delete {@link VehicleData}, helicopter blades; save fuel, etc... <b>after a driver has left the vehicle</b>.
     * @param vehicle Vehicle
     * @return False if the driver is seated in the vehicle, or if the vehicle doesn't have {@link VehicleData} and thus is not created (see {@link #enterVehicle(String, Player)} -> the vehicle can't be turned off. Otherwise, true.
     *
     * @warn Do not call this method if a vehicle is being used! Use {@link #kickOut(Player)} instead.
     */
    public static boolean turnOff(@NotNull Vehicle vehicle){
        final String licensePlate = vehicle.getLicensePlate();

        if (VehicleData.autostand.get("MTVEHICLES_MAIN_" + licensePlate) == null) return false;

        final ArmorStand standMain = VehicleData.autostand.get("MTVEHICLES_MAIN_" + licensePlate);
        final ArmorStand standSkin = VehicleData.autostand.get("MTVEHICLES_SKIN_" + licensePlate);
        final ArmorStand standMainSeat = VehicleData.autostand.get("MTVEHICLES_MAINSEAT_" + licensePlate);
        VehicleType vehicleType = VehicleData.type.get(licensePlate);

        VehicleData.lastRegions.remove(licensePlate); // doesn't do anything if not set
        VehicleData.lastRegionCheckLocation.remove(licensePlate);
        if(vehicleType == null) return true;

        if (vehicleType.isHelicopter()) {
            ArmorStand blades = VehicleData.autostand.get("MTVEHICLES_WIEKENS_" + licensePlate);
            if (blades != null && blades.isValid()) {
                Location locBelow = blades.getLocation().clone().subtract(0, 0.2, 0);
                blades.setGravity(locBelow.getBlock().getType().equals(Material.AIR)); // Blades should not fall if the helicopter is on the ground
            }
        }

        // If a helicopter is 'extremely falling' and player manages to leave it beforehand
        if (vehicleType.isHelicopter()
                && standMainSeat != null
                && (boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.EXTREME_HELICOPTER_FALL)
                && !standMainSeat.isOnGround()){
            VehicleData.fallDamage.put(licensePlate, true); // Do not damage when entering afterwards
        }

        if (!vehicleType.isBoat()) {
            standMain.setGravity(true);
            standSkin.setGravity(true);
        }
        List<Map<String, Double>> seats = vehicle.getSeats();
        for (int i = 2; i <= seats.size(); i++) {
            if (VehicleData.autostand.get("MTVEHICLES_SEAT" + i + "_" + licensePlate) != null)
                VehicleData.autostand.get("MTVEHICLES_SEAT" + i + "_" + licensePlate).remove();
        }
        if ((boolean) ConfigModule.defaultConfig.get(DefaultConfig.Option.FUEL_ENABLED) && (boolean) ConfigModule.vehicleDataConfig.get(licensePlate, VehicleDataConfig.Option.FUEL_ENABLED)) {
            double fuel = VehicleData.fuel.getOrDefault(licensePlate, vehicle.getFuel());
            ConfigModule.vehicleDataConfig.set(licensePlate, VehicleDataConfig.Option.FUEL, fuel);
            ConfigModule.vehicleDataConfig.saveToDisk();
        }

        VehicleData.clearRuntimeData(licensePlate, false);

        return true;
    }

    /**
     * @param licensePlate Vehicle's license plate
     * @see #turnOff(Vehicle)
     */
    public static boolean turnOff(@NotNull String licensePlate){
        if (getVehicle(licensePlate) == null) return false;
        return turnOff(getVehicle(licensePlate));
    }

    /**
     * Get list of seats for a vehicle (specified by license plate)
     * @see Vehicle#getSeats()
     */


    /**
     * Get vehicle's price
     * @param carUUID Vehicle's UUID
     * @return Price of the vehicle, null if UUID is not found
     */
    public static Double getPrice(String carUUID){
        List<Map<?, ?>> vehicles = ConfigModule.vehiclesConfig.getVehicles();
        for (Map<?, ?> configVehicle : vehicles) {
            List<Map<?, ?>> skins = (List<Map<?, ?>>) configVehicle.get("cars");
            for (Map<?, ?> skin : skins) {
                if (skin.get("uuid") != null) {
                    if (skin.get("uuid").equals(carUUID)) {
                        if (skin.get("uuid") != null) {
                            return (double) skin.get("price");
                        }
                    }
                }
            }
        }
        return null;
    }
}
