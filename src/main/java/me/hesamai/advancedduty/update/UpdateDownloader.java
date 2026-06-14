package me.hesamai.advancedduty.update;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class UpdateDownloader {

    private final Main plugin;

    public UpdateDownloader(Main plugin){
        this.plugin = plugin;
    }

    public void download(String downloadUrl,String version){

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try{

                URL url = new URL(downloadUrl);
                InputStream in = url.openStream();

                File updateFolder = new File(
                        plugin.getDataFolder().getParentFile(),
                        "update"
                );

                if(!updateFolder.exists()){
                    updateFolder.mkdirs();
                }

                File file = new File(updateFolder,"AdvancedDuty.jar");

                Files.copy(in,file.toPath(),StandardCopyOption.REPLACE_EXISTING);

                in.close();

                plugin.getLogger().info("Update downloaded: " + version);
                plugin.getLogger().info("The update will be applied on next server restart.");

            }
            catch(Exception e){
                plugin.getLogger().warning("Update download failed.");
                e.printStackTrace();
            }

        });
    }
}