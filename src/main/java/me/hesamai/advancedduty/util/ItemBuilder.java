package me.hesamai.advancedduty.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tiny fluent item builder used by every GUI and by the staff tools. */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material == null ? Material.STONE : material);
        this.meta = item.getItemMeta();
    }

    public static Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return m == null ? fallback : m;
    }

    public ItemBuilder name(String coloredName) {
        if (meta != null) meta.setDisplayName(MessageUtil.color(coloredName));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            List<String> colored = new ArrayList<>(lines.size());
            for (String l : lines) colored.add(MessageUtil.color(l));
            meta.setLore(colored);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (meta != null && glow) {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (e != null) {
                meta.addEnchant(e, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        return this;
    }

    public ItemBuilder hideFlags() {
        if (meta != null) meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder skullOwner(OfflinePlayer owner) {
        if (meta instanceof SkullMeta && owner != null) {
            ((SkullMeta) meta).setOwningPlayer(owner);
        }
        return this;
    }

    public ItemBuilder tag(Plugin plugin, String key, String value) {
        if (meta != null && value != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }

    public static String readTag(Plugin plugin, ItemStack stack, String key) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta m = stack.getItemMeta();
        if (m == null) return null;
        return m.getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }
}
