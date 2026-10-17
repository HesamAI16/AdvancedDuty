package me.hesamai.advancedduty.storage;

/** Backend used for inventories, playtime, history and milestone claims. */
public enum StorageType {
    YAML,
    SQLITE,
    MYSQL;

    public static StorageType fromString(String value) {
        if (value == null) return YAML;
        String v = value.trim();
        for (StorageType type : values()) {
            if (type.name().equalsIgnoreCase(v)) return type;
        }
        if (v.equalsIgnoreCase("MARIADB")) return MYSQL;
        return YAML;
    }

    public boolean isSql() {
        return this != YAML;
    }
}
