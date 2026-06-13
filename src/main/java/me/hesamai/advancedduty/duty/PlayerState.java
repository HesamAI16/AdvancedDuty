package me.hesamai.advancedduty.duty;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class PlayerState {

    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final float exp;
    private final int level;
    private final int food;
    private final double health;
    private final Location location;

    public PlayerState(GameMode gameMode,
                       boolean allowFlight,
                       boolean flying,
                       float exp,
                       int level,
                       int food,
                       double health,
                       Location location) {

        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.exp = exp;
        this.level = level;
        this.food = food;
        this.health = health;
        this.location = location == null ? null : location.clone();
    }

    public GameMode getGameMode() { return gameMode; }
    public boolean isAllowFlight() { return allowFlight; }
    public boolean isFlying() { return flying; }
    public float getExp() { return exp; }
    public int getLevel() { return level; }
    public int getFood() { return food; }
    public double getHealth() { return health; }
    public Location getLocation() { return location == null ? null : location.clone(); }

    public void write(ConfigurationSection section) {

        section.set("gamemode", gameMode.name());
        section.set("allow-flight", allowFlight);
        section.set("flying", flying);
        section.set("exp", exp);
        section.set("level", level);
        section.set("food", food);
        section.set("health", health);

        if (location != null && location.getWorld() != null) {
            section.set("location.world", location.getWorld().getName());
            section.set("location.x", location.getX());
            section.set("location.y", location.getY());
            section.set("location.z", location.getZ());
            section.set("location.yaw", location.getYaw());
            section.set("location.pitch", location.getPitch());
        }
    }

    public static PlayerState read(ConfigurationSection section) {

        if (section == null) return null;

        GameMode gm;

        try {
            gm = GameMode.valueOf(section.getString("gamemode", "SURVIVAL").toUpperCase());
        } catch (Exception e) {
            gm = GameMode.SURVIVAL;
        }

        boolean allowFlight = section.getBoolean("allow-flight");
        boolean flying = section.getBoolean("flying");

        float exp = (float) section.getDouble("exp");
        int level = section.getInt("level");

        int food = section.getInt("food");
        double health = section.getDouble("health");

        Location location = null;

        if (section.contains("location.world")) {

            World world = Bukkit.getWorld(section.getString("location.world"));

            if (world != null) {
                location = new Location(
                        world,
                        section.getDouble("location.x"),
                        section.getDouble("location.y"),
                        section.getDouble("location.z"),
                        (float) section.getDouble("location.yaw"),
                        (float) section.getDouble("location.pitch")
                );
            }
        }

        return new PlayerState(gm, allowFlight, flying, exp, level, food, health, location);
    }
}