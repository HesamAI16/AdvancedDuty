package me.hesamai.advancedduty;

import me.hesamai.advancedduty.command.DutyCommand;
import me.hesamai.advancedduty.command.StaffChatCommand;
import me.hesamai.advancedduty.cooldown.CooldownManager;
import me.hesamai.advancedduty.discord.DutyWebhookManager;
import me.hesamai.advancedduty.duty.DutyManager;
import me.hesamai.advancedduty.duty.afk.AfkListener;
import me.hesamai.advancedduty.duty.afk.AfkManager;
import me.hesamai.advancedduty.duty.inventory.storage.mysql.MySqlInventoryStorage;
import me.hesamai.advancedduty.duty.listener.DutyPlayerListener;
import me.hesamai.advancedduty.duty.log.DutyLogManager;
import me.hesamai.advancedduty.duty.migration.StorageMigrator;
import me.hesamai.advancedduty.duty.playtime.PlaytimeManager;
import me.hesamai.advancedduty.duty.playtime.mysql.MySqlPlaytimeStorage;
import me.hesamai.advancedduty.hook.LuckPermsHook;
import me.hesamai.advancedduty.lang.LanguageManager;
import me.hesamai.advancedduty.lang.gui.LanguageListener;
import me.hesamai.advancedduty.placeholder.DutyExpansion;
import me.hesamai.advancedduty.update.UpdateChecker;
import me.hesamai.advancedduty.update.UpdateDownloader;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class Main extends JavaPlugin {

    private static Main instance;

    private LanguageManager languageManager;
    private LanguageListener languageListener;
    private DutyManager dutyManager;
    private AfkManager afkManager;
    private DutyLogManager dutyLogManager;
    private DutyWebhookManager webhookManager;
    private LuckPermsHook luckPermsHook;
    private CooldownManager cooldownManager;
    private UpdateChecker updateChecker;
    private UpdateDownloader updateDownloader;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        languageManager = new LanguageManager(this);
        languageManager.load();
        getLogger().info("Language loaded: " + languageManager.getCurrentLanguage());

        LuckPerms luckPerms = setupLuckPerms();
        luckPermsHook = new LuckPermsHook(this, luckPerms);
        getLogger().info("LuckPerms: " + (luckPerms != null ? "enabled" : "disabled"));

        cooldownManager = new CooldownManager(this);

        dutyManager = new DutyManager(this, luckPermsHook);

        languageListener = new LanguageListener(this);

        if (getConfig().getString("storage.type", "YAML").equalsIgnoreCase("MYSQL")) {
            MySqlInventoryStorage mysqlInv = (MySqlInventoryStorage) dutyManager.getStorage();
            MySqlPlaytimeStorage mysqlPt   = (MySqlPlaytimeStorage) dutyManager.getPlaytimeManager().getStorage();
            new StorageMigrator(this).migrateYamlToMysqlIfNeeded(mysqlInv, mysqlPt);
        }

        dutyLogManager = new DutyLogManager(this);
        webhookManager = new DutyWebhookManager(this);
        afkManager = new AfkManager(this);

        registerCommands();
        registerListeners();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DutyExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        afkManager.start();
        dutyLogManager.startCleanupTask();

        if (getConfig().getBoolean("proxy-mode.enabled", false)) {
            int interval = getConfig().getInt("proxy-mode.sync-interval-seconds", 60) * 20;
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                for (UUID uuid : dutyManager.getOnDutyPlayers()) {
                    dutyManager.getPlaytimeManager().saveAsync(uuid);
                }
            }, interval, interval);
        }

        updateChecker = new UpdateChecker(this);
        updateDownloader = new UpdateDownloader(this);

        if(getConfig().getBoolean("update.check-on-startup")){
            updateChecker.check((latest,current,update,url)->{

                if(update){

                    getLogger().info("New version available: " + latest);

                    if(getConfig().getBoolean("update.auto-download") && url != null){
                        updateDownloader.download(url, latest);
                    }

                }else{
                    getLogger().info("Plugin is up to date.");
                }

            });
        }

        getLogger().info("AdvancedDuty enabled.");
    }

    @Override
    public void onDisable() {
        if (dutyManager != null) dutyManager.shutdown();
        getLogger().info("AdvancedDuty disabled.");
    }

    private void registerCommands() {
        DutyCommand dutyCommand = new DutyCommand(this, dutyManager, languageManager);
        if (getCommand("duty") != null) {
            getCommand("duty").setExecutor(dutyCommand);
            getCommand("duty").setTabCompleter(dutyCommand);
        } else {
            getLogger().warning("Command 'duty' not found in plugin.yml!");
        }

        if (getCommand("staffchat") != null) {
            getCommand("staffchat").setExecutor(new StaffChatCommand(this, languageManager));
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new DutyPlayerListener(dutyManager), this);
        Bukkit.getPluginManager().registerEvents(new AfkListener(afkManager, this), this);
        Bukkit.getPluginManager().registerEvents(languageListener, this);
    }

    public void reloadPlugin() {
        reloadConfig();
        if (languageManager != null) languageManager.load();
    }

    private LuckPerms setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            getLogger().warning("LuckPerms not found. Integration disabled.");
            return null;
        }
        return provider.getProvider();
    }

    public static Main getInstance() {
        return instance;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public LanguageListener getLanguageListener() {
        return languageListener;
    }

    public DutyManager getDutyManager() {
        return dutyManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public DutyLogManager getDutyLogManager() {
        return dutyLogManager;
    }

    public DutyWebhookManager getWebhookManager() {
        return webhookManager;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public PlaytimeManager getPlaytimeManager() {
        return dutyManager.getPlaytimeManager();
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public UpdateChecker getUpdateChecker(){
        return updateChecker;
    }

    public UpdateDownloader getUpdateDownloader(){
        return updateDownloader;
    }
}