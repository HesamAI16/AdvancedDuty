package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.util.MessageUtil;
import me.hesamai.advancedduty.util.PapiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freezes a suspected cheater for a screenshare: no moving, building, hitting or commands; the frozen
 * player's chat only reaches staff. Frozen players are remembered across restarts, and a configurable
 * punishment runs if one logs out.
 */
public class FreezeManager implements Listener {

    private final Main plugin;
    private final Map<UUID, String> frozen = new ConcurrentHashMap<>(); // uuid -> frozen-by name
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private BukkitTask reminder;

    public FreezeManager(Main plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle / persistence

    private File file() {
        return new File(plugin.getDataFolder(), "data/frozen.yml");
    }

    public void start() {
        load();
        long every = Math.max(0, plugin.getConfig().getLong("freeze.reminder-seconds", 5)) * 20L;
        if (every > 0) {
            reminder = Bukkit.getScheduler().runTaskTimer(plugin, this::remind, every, every);
        }
        for (UUID id : frozen.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) applyEffects(p);
        }
    }

    public void stop() {
        if (reminder != null) reminder.cancel();
        reminder = null;
        for (UUID id : frozen.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) removeEffects(p);
        }
        save();
    }

    private void load() {
        frozen.clear();
        File f = file();
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        if (!cfg.isConfigurationSection("frozen")) return;
        for (String key : cfg.getConfigurationSection("frozen").getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                frozen.put(id, cfg.getString("frozen." + key + ".by", "?"));
                names.put(id, cfg.getString("frozen." + key + ".name", key));
            } catch (IllegalArgumentException ignored) {
                // skip garbage entries
            }
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : frozen.entrySet()) {
            cfg.set("frozen." + e.getKey() + ".by", e.getValue());
            cfg.set("frozen." + e.getKey() + ".name", names.getOrDefault(e.getKey(), e.getKey().toString()));
        }
        try {
            File f = file();
            f.getParentFile().mkdirs();
            cfg.save(f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save frozen players: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ api

    public boolean isFrozen(UUID id) {
        return frozen.containsKey(id);
    }

    public int count() {
        return frozen.size();
    }

    public void toggle(org.bukkit.command.CommandSender by, Player target) {
        if (isFrozen(target.getUniqueId())) unfreeze(by, target);
        else freeze(by, target);
    }

    public void freeze(org.bukkit.command.CommandSender by, Player target) {
        if (target.hasPermission("advancedduty.freeze.exempt") && !by.hasPermission("advancedduty.freeze.exempt.override")) {
            plugin.getLanguageManager().send(by, "freeze-exempt", "{player}", target.getName());
            return;
        }
        frozen.put(target.getUniqueId(), by.getName());
        names.put(target.getUniqueId(), target.getName());
        save();
        applyEffects(target);
        plugin.getLanguageManager().send(target, "frozen-notice");
        sendTitle(target);
        plugin.getLanguageManager().send(by, "freeze-done", "{player}", target.getName());
        notifyStaff("freeze-staff-notify", target.getName(), by.getName());
        plugin.getDiscord().sendEvent("freeze", target.getName(), by.getName());
    }

    public void unfreeze(org.bukkit.command.CommandSender by, Player target) {
        if (frozen.remove(target.getUniqueId()) == null) return;
        save();
        removeEffects(target);
        plugin.getLanguageManager().send(target, "unfrozen-notice");
        plugin.getLanguageManager().send(by, "unfreeze-done", "{player}", target.getName());
        notifyStaff("unfreeze-staff-notify", target.getName(), by.getName());
    }

    /** Unfreezes a player who is offline (command by name). */
    public boolean unfreezeOffline(UUID id) {
        boolean r = frozen.remove(id) != null;
        if (r) save();
        return r;
    }

    private void notifyStaff(String key, String target, String by) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("advancedduty.freeze") || p.hasPermission("advancedduty.staffchat.receive")) {
                plugin.getLanguageManager().send(p, key, "{player}", target, "{staff}", by);
            }
        }
    }

    private void applyEffects(Player p) {
        for (String raw : plugin.getConfig().getStringList("freeze.effects")) {
            PotionEffect e = me.hesamai.advancedduty.duty.profile.ProfileConfig.parseEffect(raw);
            if (e != null) p.addPotionEffect(e, true);
        }
    }

    private void removeEffects(Player p) {
        for (String raw : plugin.getConfig().getStringList("freeze.effects")) {
            PotionEffect e = me.hesamai.advancedduty.duty.profile.ProfileConfig.parseEffect(raw);
            if (e != null) p.removePotionEffect(e.getType());
        }
    }

    private void sendTitle(Player p) {
        if (!plugin.getConfig().getBoolean("freeze.title.enabled", true)) return;
        String t = MessageUtil.color(plugin.getConfig().getString("freeze.title.title", "&b&lFROZEN"));
        String s = MessageUtil.color(plugin.getConfig().getString("freeze.title.subtitle", "&7Do not log out - join the screenshare"));
        p.sendTitle(t, s, 5, 60, 10);
    }

    private void remind() {
        for (UUID id : frozen.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            plugin.getLanguageManager().send(p, "frozen-reminder");
            if (plugin.getConfig().getBoolean("freeze.reminder-title", false)) sendTitle(p);
        }
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!isFrozen(e.getPlayer().getUniqueId()) || e.getTo() == null) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        e.setTo(back);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvOpen(InventoryOpenEvent e) {
        if (plugin.getConfig().getBoolean("freeze.block-inventories", true) && isFrozen(e.getPlayer().getUniqueId())
                && e.getInventory().getHolder() != e.getPlayer()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (plugin.getConfig().getBoolean("freeze.block-inventories", true) && isFrozen(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && isFrozen(e.getEntity().getUniqueId())
                && plugin.getConfig().getBoolean("freeze.invulnerable", true)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player && isFrozen(e.getDamager().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!isFrozen(e.getPlayer().getUniqueId())) return;
        List<String> allowed = plugin.getConfig().getStringList("freeze.allowed-commands");
        if (me.hesamai.advancedduty.util.CommandMatcher.matches(e.getMessage(), allowed)) return;
        e.setCancelled(true);
        plugin.getLanguageManager().send(e.getPlayer(), "frozen-command-blocked");
    }

    /** The frozen player talks to the staff team only. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!isFrozen(e.getPlayer().getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("freeze.private-chat", true)) return;
        e.getRecipients().removeIf(r -> !r.equals(e.getPlayer())
                && !(r.hasPermission("advancedduty.freeze") || r.hasPermission("advancedduty.staffchat.receive")));
        String prefix = plugin.getLanguageManager().raw("frozen-chat-prefix");
        e.setFormat(prefix.replace("%", "%%") + e.getFormat());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!isFrozen(p.getUniqueId())) return;
        names.put(p.getUniqueId(), p.getName());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            applyEffects(p);
            plugin.getLanguageManager().send(p, "frozen-notice");
            notifyStaff("freeze-rejoin-notify", p.getName(), frozen.getOrDefault(p.getUniqueId(), "?"));
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!isFrozen(p.getUniqueId())) return;
        notifyStaff("freeze-quit-notify", p.getName(), frozen.getOrDefault(p.getUniqueId(), "?"));
        plugin.getDiscord().sendEvent("freeze-quit", p.getName(), frozen.getOrDefault(p.getUniqueId(), "?"));
        List<String> commands = new ArrayList<>(plugin.getConfig().getStringList("freeze.on-quit-commands"));
        for (String c : commands) {
            String cmd = PapiUtil.apply(p, c.replace("{player}", p.getName()).replace("{uuid}", p.getUniqueId().toString()));
            if (cmd.toLowerCase(Locale.ROOT).startsWith("console:")) cmd = cmd.substring(8).trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            final String run = cmd;
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run));
        }
        if (plugin.getConfig().getBoolean("freeze.unfreeze-after-quit-commands", false) && !commands.isEmpty()) {
            frozen.remove(p.getUniqueId());
            save();
        }
    }
}
