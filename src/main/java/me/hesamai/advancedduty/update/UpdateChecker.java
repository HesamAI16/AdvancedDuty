package me.hesamai.advancedduty.update;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final Main plugin;

    public UpdateChecker(Main plugin){
        this.plugin = plugin;
    }

    public void check(UpdateCallback callback){

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try{

                String owner = plugin.getConfig().getString("update.github.owner");
                String repo = plugin.getConfig().getString("update.github.repo");

                URL url = new URL("https://api.github.com/repos/"+owner+"/"+repo+"/releases/latest");

                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("User-Agent","AdvancedDuty");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(con.getInputStream())
                );

                StringBuilder json = new StringBuilder();
                String line;

                while((line = reader.readLine()) != null){
                    json.append(line);
                }

                reader.close();

                String body = json.toString();

                String latest = body.split("\"tag_name\":\"")[1].split("\"")[0];

                String downloadUrl = null;

                String[] parts = body.split("\"browser_download_url\":\"");

                for(String part : parts){
                    if(part.contains(".jar")){
                        downloadUrl = part.split("\"")[0];
                        break;
                    }
                }

                String current = plugin.getDescription().getVersion();

                boolean update = !current.equalsIgnoreCase(latest);

                callback.result(latest,current,update,downloadUrl);

            }catch(Exception e){
                plugin.getLogger().warning("Update check failed: "+e.getMessage());
            }

        });
    }

    @FunctionalInterface
    public interface UpdateCallback{
        void result(String latest,String current,boolean update,String downloadUrl);
    }

}