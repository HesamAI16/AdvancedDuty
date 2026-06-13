package me.hesamai.advancedduty.duty.inventory.storage;

public enum StorageType {
    YAML,
    MYSQL;

    public static StorageType fromString(String value) {
        if (value == null) {
            return YAML;
        }

        for (StorageType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        return YAML;
    }
}