package me.hesamai.advancedduty.duty.inventory.storage;

import me.hesamai.advancedduty.duty.DutyStateSnapshot;

import java.util.UUID;

public interface InventoryDataStorage {

    void initialize();

    DutyStateSnapshot load(UUID uuid);

    void save(UUID uuid, DutyStateSnapshot snapshot);

    void delete(UUID uuid);

    void shutdown();
}