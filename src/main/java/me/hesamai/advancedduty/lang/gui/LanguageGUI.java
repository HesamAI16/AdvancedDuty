package me.hesamai.advancedduty.lang.gui;

import me.hesamai.advancedduty.Main;
import me.hesamai.advancedduty.lang.LanguageManager;
import me.hesamai.advancedduty.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Language selector. Names / icons come from {@code meta.name} / {@code meta.icon} of each language file. */
public class LanguageGUI {

    static final int[] LANGUAGE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    static final int PREVIOUS_SLOT = 47;
    static final int INFO_SLOT = 49;
    static final int NEXT_SLOT = 51;

    private LanguageGUI() {
    }

    public static void open(Main plugin, Player player, int page) {
        LanguageManager lang = plugin.getLanguageManager();
        File[] files = lang.getLanguageFiles();

        int perPage = LANGUAGE_SLOTS.length;
        int maxPage = Math.max(1, (int) Math.ceil((double) files.length / perPage));
        int current = Math.max(1, Math.min(page, maxPage));

        LanguageHolder holder = new LanguageHolder(LanguageHolder.Type.MAIN, current);
        Inventory inv = holder.create(54, lang.gui("language.title", "{page}", String.valueOf(current), "{max}", String.valueOf(maxPage)));
        border(inv);

        int start = (current - 1) * perPage;
        for (int i = start, slot = 0; i < Math.min(files.length, start + perPage); i++, slot++) {
            String code = files[i].getName().replaceFirst("(?i)\\.yml$", "").toLowerCase(Locale.ROOT);
            inv.setItem(LANGUAGE_SLOTS[slot], languageItem(plugin, code, files[i].getName()));
            holder.getSlots().put(LANGUAGE_SLOTS[slot], code);
        }
        inv.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(lang.gui("previous")).build());
        inv.setItem(NEXT_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(lang.gui("next")).build());
        List<String> lore = new ArrayList<>(lang.guiList("language.info-lore",
                "{page}", String.valueOf(current), "{max}", String.valueOf(maxPage),
                "{total}", String.valueOf(files.length), "{current}", lang.getLanguageName(lang.getCurrentLanguage())));
        inv.setItem(INFO_SLOT, new ItemBuilder(Material.BOOK).name(lang.gui("language.info")).lore(lore).build());

        player.openInventory(inv);
    }

    public static void openConfirm(Main plugin, Player player, String code, int returnPage) {
        LanguageManager lang = plugin.getLanguageManager();
        LanguageHolder holder = new LanguageHolder(LanguageHolder.Type.CONFIRM, returnPage);
        holder.setSelected(code);
        Inventory inv = holder.create(27, lang.gui("language.confirm-title"));
        border(inv);
        inv.setItem(11, new ItemBuilder(Material.RED_CONCRETE).name(lang.gui("language.cancel")).build());
        inv.setItem(13, languageItem(plugin, code, code + ".yml"));
        inv.setItem(15, new ItemBuilder(Material.LIME_CONCRETE).name(lang.gui("language.confirm")).build());
        player.openInventory(inv);
    }

    private static void border(Inventory inv) {
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        int size = inv.getSize();
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = size - 9; i < size; i++) inv.setItem(i, pane);
        for (int i = 9; i < size - 9; i += 9) inv.setItem(i, pane);
        for (int i = 17; i < size - 9; i += 9) inv.setItem(i, pane);
    }

    private static ItemStack languageItem(Main plugin, String code, String fileName) {
        LanguageManager lang = plugin.getLanguageManager();
        boolean active = lang.getCurrentLanguage().equalsIgnoreCase(code);
        Material icon = ItemBuilder.material(lang.getLanguageIcon(code), Material.PAPER);
        List<String> lore = new ArrayList<>(lang.guiList("language.item-lore", "{file}", fileName));
        lore.add(" ");
        lore.add(lang.gui(active ? "language.current" : "language.click"));
        return new ItemBuilder(icon)
                .name((active ? "&a" : "&b") + lang.getLanguageName(code))
                .lore(lore).glow(active).hideFlags().build();
    }
}
