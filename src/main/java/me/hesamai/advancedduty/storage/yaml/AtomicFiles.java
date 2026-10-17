package me.hesamai.advancedduty.storage.yaml;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Crash-safe YAML file helpers: write to a temp file and rename, optionally keep a ".bak". */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    public static YamlConfiguration loadYaml(File file) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new IOException("Invalid YAML in " + file.getName() + ": " + ex.getMessage(), ex);
        }
        return cfg;
    }

    public static void saveYaml(YamlConfiguration cfg, File target, boolean keepBackup) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        Path tmp = new File(parent, target.getName() + ".tmp").toPath();
        Files.write(tmp, cfg.saveToString().getBytes(StandardCharsets.UTF_8));

        if (keepBackup && target.exists()) {
            try {
                Files.copy(target.toPath(), new File(parent, target.getName() + ".bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // a failed backup must not block the real save
            }
        }
        try {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Renames an unreadable file out of the way so it is never overwritten by empty data. */
    public static File quarantine(File file) {
        File dest = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
        if (file.renameTo(dest)) return dest;
        return null;
    }
}
