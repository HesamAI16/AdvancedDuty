package me.hesamai.advancedduty.duty.playtime;

import java.util.Map;
import java.util.UUID;

public interface PlaytimeStorage {
    long load(UUID uuid);
    void save(UUID uuid, long ms);void close();

    // PlaytimeStorage interface
    Map<UUID, Long> loadAll();
}