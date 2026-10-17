package me.hesamai.advancedduty;

import me.hesamai.advancedduty.api.AdvancedDutyAPI;
import me.hesamai.advancedduty.command.DutyCommand;
import me.hesamai.advancedduty.command.StaffChatCommand;
import me.hesamai.advancedduty.command.StaffListCommand;
import me.hesamai.advancedduty.config.ConfigUpdater;
import me.hesamai.advancedduty.cooldown.CooldownManager;
import me.hesamai.advancedduty.discord.DiscordManager;
import me.hesamai.advancedduty.duty.DutyManager;
import me.hesamai.advancedduty.duty.afk.AfkListener;
import me.hesamai.advancedduty.duty.afk.AfkManager;
import me.hesamai.advancedduty.duty.history.HistoryManager;
import me.hesamai.advancedduty.duty.listener.DutyPlayerListener;
import me.hesamai.advancedduty.duty.listener.RestrictionListener;
import me.hesamai.advancedduty.duty.log.DutyLogManager;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.feature.DisplayManager;
import me.hesamai.advancedduty.feature.FreezeManager;
import me.hesamai.advancedduty.feature.InspectManager;
import me.hesamai.advancedduty.feature.MilestoneManager;
import me.hesamai.advancedduty.feature.NetworkManager;
import me.hesamai.advancedduty.feature.StaffChatManager;
import me.hesamai.advancedduty.feature.StaffList;
import me.hesamai.advancedduty.feature.ToolsManager;
import me.hesamai.advancedduty.feature.VanishManager;
import me.hesamai.advancedduty.gui.StaffGui;
import me.hesamai.advancedduty.hook.LuckPermsHook;
import me.hesamai.advancedduty.lang.LanguageManager;
import me.hesamai.advancedduty.lang.gui.LanguageListener;
import me.hesamai.advancedduty.placeholder.DutyExpansion;
import me.hesamai.advancedduty.storage.DataManager;
import me.hesamai.advancedduty.update.UpdateManager;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {

    private static Main instance;

    private DataManager data;
    private LanguageManager languageManager;
    private LuckPermsHook luckPerms;
    private CooldownManager cooldownManager;
    private PlaytimeManager playtime;
    private HistoryManager history;
    private DutyManager dutyManager;
    private AfkManager afkManager;
    private DutyLogManager dutyLog;
    private DiscordManager discord;
    private VanishManager vanish;
    private FreezeManager freeze;
    private ToolsManager tools;
    private InspectManager inspect;
    private DisplayManager displays;
    private MilestoneManager milestones;
    private StaffChatManager staffChat;
    private StaffList staffList;
    private NetworkManager network;
    private StaffGui staffGui;
    private UpdateManager updates;
    private DutyExpansion expansion;
    private boolean started;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        if (getConfig().getBoolean("auto-update-files", true)) {
            ConfigUpdater.update(this, new File(getDataFolder(), "config.yml"), "config.yml", ConfigUpdater.DYNAMIC_SECTIONS);
        }
        reloadConfig();

        languageManager = new LanguageManager(this);
        languageManager.load();

        data = new DataManager(this);
        try {
            data.init();
        } catch (Throwable ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not start the storage - disabling AdvancedDuty so no data is damaged.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        luckPerms = new LuckPermsHook(this, setupLuckPerms());
        getLogger().info("LuckPerms: " + (luckPerms.getLuckPerms() != null ? "enabled" : "not found (optional)"));

        cooldownManager = new CooldownManager(this);
        playtime = new PlaytimeManager(this);
        history = new HistoryManager(this);
        vanish = new VanishManager(this);
        freeze = new FreezeManager(this);
        tools = new ToolsManager(this);
        inspect = new InspectManager(this);
        displays = new DisplayManager(this);
        milestones = new MilestoneManager(this);
        staffChat = new StaffChatManager(this);
        staffList = new StaffList(this);
        network = new NetworkManager(this);
        staffGui = new StaffGui(this);
        discord = new DiscordManager(this);
        dutyLog = new DutyLogManager(this);
        afkManager = new AfkManager(this);
        updates = new UpdateManager(this);
        dutyManager = new DutyManager(this);

        registerCommands();
        registerListeners();

        playtime.start();
        dutyManager.start();
        afkManager.start();
        displays.start();
        milestones.start();
        freeze.start();
        network.start();
        discord.start();
        dutyLog.startCleanupTask();
        updates.start();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new DutyExpansion(this);
            expansion.register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        AdvancedDutyAPI.init(this);
        started = true;

        // players that are already online (/reload or late enable): load their data and restore their state
        for (Player p : Bukkit.getOnlinePlayers()) {
            dutyManager.handleJoin(p);
        }
        getLogger().info("AdvancedDuty " + getDescription().getVersion() + " enabled (storage: " + data.activeType() + ").");
    }

    @Override
    public void onDisable() {
        if (started) {
            try {
                if (expansion != null) expansion.unregister();
            } catch (Throwable ignored) {
                // PlaceholderAPI is already gone
            }
            updates.stop();
            milestones.stop();
            discord.stop();
            network.stop();
            freeze.stop();
            afkManager.stop();
            dutyLog.stop();
            dutyManager.shutdown();
            displays.shutdown();
            playtime.shutdown();
            AdvancedDutyAPI.shutdown();
        }
        if (data != null) data.shutdown();
        instance = null;
        getLogger().info("AdvancedDuty disabled.");
    }

    private void registerCommands() {
        DutyCommand duty = new DutyCommand(this);
        PluginCommand c = getCommand("duty");
        if (c != null) {
            c.setExecutor(duty);
            c.setTabCompleter(duty);
        }
        PluginCommand sc = getCommand("staffchat");
        if (sc != null) sc.setExecutor(new StaffChatCommand(this));
        PluginCommand sl = getCommand("stafflist");
        if (sl != null) sl.setExecutor(new StaffListCommand(this));
    }

    private void registerListeners() {
        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new DutyPlayerListener(this), this);
        pm.registerEvents(new RestrictionListener(this), this);
        pm.registerEvents(new AfkListener(afkManager, this), this);
        pm.registerEvents(new LanguageListener(this), this);
        pm.registerEvents(vanish, this);
        pm.registerEvents(freeze, this);
        pm.registerEvents(tools, this);
        pm.registerEvents(inspect, this);
        pm.registerEvents(staffChat, this);
        pm.registerEvents(staffGui, this);
    }

    /** Reloads config.yml and the language files and re-applies displays (does not touch the storage backend). */
    public void reloadPlugin() {
        if (getConfig().getBoolean("auto-update-files", true)) {
            ConfigUpdater.update(this, new File(getDataFolder(), "config.yml"), "config.yml", ConfigUpdater.DYNAMIC_SECTIONS);
        }
        reloadConfig();
        languageManager.load();
        afkManager.start();
        displays.start();
        milestones.start();
        discord.start();
        dutyLog.startCleanupTask();
        updates.start();
        dutyManager.refreshOnline();
        if (me.hesamai.advancedduty.storage.StorageType.fromString(getConfig().getString("storage.type", "YAML")) != data.activeType()) {
            getLogger().warning("storage.type changed - restart the server to switch the storage backend.");
        }
    }

    private LuckPerms setupLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return null;
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        return provider == null ? null : provider.getProvider();
    }

    /** The jar this plugin was loaded from (used by the updater). */
    public File jarFile() {
        return getFile();
    }

    public static Main getInstance() { return instance; }
    public DataManager getData() { return data; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public LuckPermsHook getLuckPerms() { return luckPerms; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public PlaytimeManager getPlaytime() { return playtime; }
    public HistoryManager getHistory() { return history; }
    public DutyManager getDutyManager() { return dutyManager; }
    public AfkManager getAfk() { return afkManager; }
    public DutyLogManager getDutyLog() { return dutyLog; }
    public DiscordManager getDiscord() { return discord; }
    public VanishManager getVanish() { return vanish; }
    public FreezeManager getFreeze() { return freeze; }
    public ToolsManager getTools() { return tools; }
    public InspectManager getInspect() { return inspect; }
    public DisplayManager getDisplays() { return displays; }
    public MilestoneManager getMilestones() { return milestones; }
    public StaffChatManager getStaffChat() { return staffChat; }
    public StaffList getStaffList() { return staffList; }
    public NetworkManager getNetwork() { return network; }
    public StaffGui getStaffGui() { return staffGui; }
    public UpdateManager getUpdates() { return updates; }
}
