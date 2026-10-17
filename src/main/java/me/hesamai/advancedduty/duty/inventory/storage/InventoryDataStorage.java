package me.hesamai.advancedduty.duty.inventory.storage;

import me.hesamai.advancedduty.duty.DutyStateSnapshot;

import java.util.UUID;

/** Persistence of the on/off duty inventories, saved states and the active duty session of a player. */
public interface InventoryDataStorage {

    void initialize() throws Exception;

    /**
     * @return the stored snapshot, or {@code null} if nothing is stored for this player
     * @throws Exception when data exists but cannot be read - callers must then NOT overwrite it
     */
    DutyStateSnapshot load(UUID uuid) throws Exception;

    void save(UUID uuid, DutyStateSnapshot snapshot) throws Exception;

    void delete(UUID uuid) throws Exception;

    void shutdown();
}
