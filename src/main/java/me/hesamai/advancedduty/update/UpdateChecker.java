package me.hesamai.advancedduty.update;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;

import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final Main plugin;

    private static final String LATEST_URL =
            "https://github.com/HesamAI16/AdvancedDuty/releases/latest";

    public UpdateChecker(Main plugin){
        this.plugin = plugin;
    }

    public void check(UpdateCallback callback){

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try{

                HttpURLConnection con =
                        (HttpURLConnection) new URL(LATEST_URL).openConnection();

                con.setInstanceFollowRedirects(false);
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);

                String location = con.getHeaderField("Location");

                if(location == null){
                    plugin.getLogger().warning("Failed to detect latest release.");
                    return;
                }

                String latest = location.substring(location.lastIndexOf("/") + 1);
                latest = latest.replaceFirst("^v","");

                String current = plugin.getDescription().getVersion();

                boolean updateAvailable = !current.equalsIgnoreCase(latest);

                String downloadUrl =
                        "https://github.com/HesamAI16/AdvancedDuty/releases/download/v"
                                + latest +
                                "/AdvancedDuty-" + latest + ".jar";

                callback.result(latest,current,updateAvailable,downloadUrl);

            }
            catch(Exception e){
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
            }

        });
    }

    @FunctionalInterface
    public interface UpdateCallback{
        void result(String latest,String current,boolean update,String downloadUrl);
    }
}