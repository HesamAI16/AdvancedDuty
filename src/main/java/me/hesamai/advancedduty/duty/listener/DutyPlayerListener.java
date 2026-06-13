package me.hesamai.advancedduty.duty.listener;

import me.hesamai.advancedduty.duty.DutyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DutyPlayerListener implements Listener {

    private final DutyManager dutyManager;

    public DutyPlayerListener(DutyManager dutyManager) {
        this.dutyManager = dutyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        dutyManager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dutyManager.handleQuit(event.getPlayer());
    }
}