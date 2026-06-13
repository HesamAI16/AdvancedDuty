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

                File file = new File(
                        plugin.getDataFolder().getParentFile(),
                        "AdvancedDuty-"+version+".jar"
                );

                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);

                in.close();

                plugin.getLogger().info("Downloaded new version: "+version);

            }catch(Exception e){
                plugin.getLogger().warning("Update download failed.");
                e.printStackTrace();
            }

        });
    }

}