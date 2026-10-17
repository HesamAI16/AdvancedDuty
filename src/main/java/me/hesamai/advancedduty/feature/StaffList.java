package me.hesamai.advancedduty.feature;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds the list of online staff (this server + the rest of the network). Server thread only. */
public class StaffList {

    private final Main plugin;

    public StaffList(Main plugin) {
        this.plugin = plugin;
    }

    public List<StaffEntry> snapshot(boolean includeVanished) {
        List<StaffEntry> list = new ArrayList<>();
        String server = plugin.getNetwork().isEnabled() ? plugin.getNetwork().getServerName() : null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("advancedduty.use")) continue;
            UUID id = p.getUniqueId();
            boolean vanished = plugin.getVanish().isVanished(id);
            if (vanished && !includeVanished) continue;
            boolean on = plugin.getDutyManager().isOnDuty(id);
            list.add(new StaffEntry(id, p.getName(), server, on, on ? plugin.getPlaytime().sessionStart(id) : 0L,
                    plugin.getAfk().isAfk(id), vanished));
        }
        if (plugin.getNetwork().isEnabled()) {
            for (StaffEntry e : plugin.getNetwork().getRemoteStaff()) {
                if (e.isVanished() && !includeVanished) continue;
                list.add(e);
            }
        }
        list.sort(Comparator.comparing((StaffEntry e) -> !e.isOnDuty()).thenComparing(StaffEntry::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public int countOnDuty(boolean includeVanished) {
        int n = 0;
        for (StaffEntry e : snapshot(includeVanished)) if (e.isOnDuty()) n++;
        return n;
    }
}
