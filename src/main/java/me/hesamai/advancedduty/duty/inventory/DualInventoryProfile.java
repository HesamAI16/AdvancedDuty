package me.hesamai.advancedduty.duty.inventory;

import me.hesamai.advancedduty.duty.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DualInventoryProfile {

    private final UUID playerId;

    private PlayerInventoryState offDutyInventory;
    private PlayerInventoryState onDutyInventory;

    private PlayerState offDutyState;
    private PlayerState onDutyState;

    private List<String> storedStaffGroups = new ArrayList<String>();
    private List<String> storedStaffPermissions = new ArrayList<String>();

    public DualInventoryProfile(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public PlayerInventoryState getOffDutyInventory() {
        return offDutyInventory;
    }

    public void setOffDutyInventory(PlayerInventoryState offDutyInventory) {
        this.offDutyInventory = offDutyInventory;
    }

    public PlayerInventoryState getOnDutyInventory() {
        return onDutyInventory;
    }

    public void setOnDutyInventory(PlayerInventoryState onDutyInventory) {
        this.onDutyInventory = onDutyInventory;
    }

    public PlayerState getOffDutyState() {
        return offDutyState;
    }

    public void setOffDutyState(PlayerState offDutyState) {
        this.offDutyState = offDutyState;
    }

    public PlayerState getOnDutyState() {
        return onDutyState;
    }

    public void setOnDutyState(PlayerState onDutyState) {
        this.onDutyState = onDutyState;
    }

    public List<String> getStoredStaffGroups() {
        return new ArrayList<String>(storedStaffGroups);
    }

    public void setStoredStaffGroups(List<String> storedStaffGroups) {
        this.storedStaffGroups = storedStaffGroups == null ? new ArrayList<String>() : new ArrayList<String>(storedStaffGroups);
    }

    public List<String> getStoredStaffPermissions() {
        return new ArrayList<String>(storedStaffPermissions);
    }

    public void setStoredStaffPermissions(List<String> storedStaffPermissions) {
        this.storedStaffPermissions = storedStaffPermissions == null ? new ArrayList<String>() : new ArrayList<String>(storedStaffPermissions);
    }

    public DualInventoryProfile copy() {

        DualInventoryProfile copy = new DualInventoryProfile(playerId);

        copy.setOffDutyInventory(offDutyInventory == null ? null : offDutyInventory.copy());
        copy.setOnDutyInventory(onDutyInventory == null ? null : onDutyInventory.copy());

        copy.setOffDutyState(offDutyState);
        copy.setOnDutyState(onDutyState);

        copy.setStoredStaffGroups(getStoredStaffGroups());
        copy.setStoredStaffPermissions(getStoredStaffPermissions());

        return copy;
    }
}