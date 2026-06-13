package me.hesamai.advancedduty.duty;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

public final class DutySession {

    private final UUID playerId;
    private final long startTime;
    private final String reason;
    private final GameMode previousGameMode;
    private final boolean previousAllowFlight;
    private final boolean previousFlying;
    private final float previousExp;
    private final int previousLevel;
    private final int previousFoodLevel;
    private final double previousHealth;
    private final Location previousLocation;

    public DutySession(UUID playerId,
                       long startTime,
                       String reason,
                       GameMode previousGameMode,
                       boolean previousAllowFlight,
                       boolean previousFlying,
                       float previousExp,
                       int previousLevel,
                       int previousFoodLevel,
                       double previousHealth,
                       Location previousLocation) {
        this.playerId = playerId;
        this.startTime = startTime;
        this.reason = reason;
        this.previousGameMode = previousGameMode;
        this.previousAllowFlight = previousAllowFlight;
        this.previousFlying = previousFlying;
        this.previousExp = previousExp;
        this.previousLevel = previousLevel;
        this.previousFoodLevel = previousFoodLevel;
        this.previousHealth = previousHealth;
        this.previousLocation = previousLocation == null ? null : previousLocation.clone();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public long getStartTime() {
        return startTime;
    }

    public String getReason() {
        return reason;
    }

    public GameMode getPreviousGameMode() {
        return previousGameMode;
    }

    public boolean isPreviousAllowFlight() {
        return previousAllowFlight;
    }

    public boolean isPreviousFlying() {
        return previousFlying;
    }

    public float getPreviousExp() {
        return previousExp;
    }

    public int getPreviousLevel() {
        return previousLevel;
    }

    public int getPreviousFoodLevel() {
        return previousFoodLevel;
    }

    public double getPreviousHealth() {
        return previousHealth;
    }

    public Location getPreviousLocation() {
        return previousLocation == null ? null : previousLocation.clone();
    }

    public void writeTo(ConfigurationSection section) {
        section.set("player-id", playerId.toString());
        section.set("start-time", startTime);
        section.set("reason", reason);
        section.set("previous.gamemode", previousGameMode == null ? "SURVIVAL" : previousGameMode.name());
        section.set("previous.allow-flight", previousAllowFlight);
        section.set("previous.flying", previousFlying);
        section.set("previous.exp", previousExp);
        section.set("previous.level", previousLevel);
        section.set("previous.food", previousFoodLevel);
        section.set("previous.health", previousHealth);

        if (previousLocation != null && previousLocation.getWorld() != null) {
            section.set("previous.location.world", previousLocation.getWorld().getName());
            section.set("previous.location.x", previousLocation.getX());
            section.set("previous.location.y", previousLocation.getY());
            section.set("previous.location.z", previousLocation.getZ());
            section.set("previous.location.yaw", previousLocation.getYaw());
            section.set("previous.location.pitch", previousLocation.getPitch());
        }
    }

    public static DutySession readFrom(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String uuidRaw = section.getString("player-id");
        if (uuidRaw == null || uuidRaw.isEmpty()) {
            return null;
        }

        UUID playerId = UUID.fromString(uuidRaw);
        long startTime = section.getLong("start-time", System.currentTimeMillis());
        String reason = section.getString("reason", null);

        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(section.getString("previous.gamemode", "SURVIVAL").toUpperCase());
        } catch (IllegalArgumentException ex) {
            gameMode = GameMode.SURVIVAL;
        }

        boolean allowFlight = section.getBoolean("previous.allow-flight", false);
        boolean flying = section.getBoolean("previous.flying", false);
        float exp = (float) section.getDouble("previous.exp", 0.0D);
        int level = section.getInt("previous.level", 0);
        int food = section.getInt("previous.food", 20);
        double health = section.getDouble("previous.health", 20.0D);

        Location location = null;
        String worldName = section.getString("previous.location.world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                location = new Location(
                        world,
                        section.getDouble("previous.location.x"),
                        section.getDouble("previous.location.y"),
                        section.getDouble("previous.location.z"),
                        (float) section.getDouble("previous.location.yaw"),
                        (float) section.getDouble("previous.location.pitch")
                );
            }
        }

        return new DutySession(
                playerId,
                startTime,
                reason,
                gameMode,
                allowFlight,
                flying,
                exp,
                level,
                food,
                health,
                location
        );
    }
}