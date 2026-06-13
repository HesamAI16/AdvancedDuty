package me.hesamai.advancedduty.lang.gui;

import me.hesamai.advancedduty.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.File;
import java.util.*;

public class LanguageGUI {

    public static final String TITLE = "Languages";
    public static final String CONFIRM_TITLE = "Confirm Language";

    private static final int[] LANGUAGE_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34
    };

    private static final int PREVIOUS_SLOT = 47;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 51;

    public static void open(Main plugin, Player player, int page) {

        File[] files = plugin.getLanguageManager().getLanguageFiles();
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        int perPage = LANGUAGE_SLOTS.length;
        int maxPage = Math.max(1,(int)Math.ceil((double)files.length/perPage));
        int currentPage = Math.max(1,Math.min(page,maxPage));

        Inventory inv = Bukkit.createInventory(
                new LanguageHolder(LanguageHolder.Type.MAIN,currentPage),
                54,
                TITLE + " - Page " + currentPage
        );

        createBorder(inv);

        int start = (currentPage-1)*perPage;
        int end = Math.min(start+perPage,files.length);

        int slotIndex = 0;

        for(int i=start;i<end;i++){

            File file = files[i];
            String lang = file.getName().replace(".yml","");

            inv.setItem(
                    LANGUAGE_SLOTS[slotIndex],
                    createLanguageItem(plugin,lang,file.getName())
            );

            slotIndex++;
        }

        inv.setItem(PREVIOUS_SLOT,createPreviousItem(currentPage>1));
        inv.setItem(INFO_SLOT,createInfoItem(plugin,currentPage,maxPage,files.length));
        inv.setItem(NEXT_SLOT,createNextItem(currentPage<maxPage));

        player.setMetadata(
                "advancedduty_lang_page",
                new FixedMetadataValue(plugin,currentPage)
        );

        plugin.getLanguageListener().markMainGui(player.getUniqueId());
        player.openInventory(inv);
    }

    public static void open(Main plugin, Player player){
        open(plugin,player,1);
    }

    public static void openConfirm(Main plugin, Player player, String lang, int returnPage){

        Inventory inv = Bukkit.createInventory(
                new LanguageHolder(LanguageHolder.Type.CONFIRM,returnPage),
                27,
                CONFIRM_TITLE
        );

        createBorder(inv);

        inv.setItem(11,createCancelItem());
        inv.setItem(13,createLanguageItem(plugin,lang,lang + ".yml"));
        inv.setItem(15,createConfirmItem());

        player.openInventory(inv);
    }

    private static void createBorder(Inventory inv){

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);

        int size = inv.getSize();

        for(int i=0;i<9;i++) inv.setItem(i,pane);

        for(int i=size-9;i<size;i++) inv.setItem(i,pane);

        for(int i=9;i<size-9;i+=9) inv.setItem(i,pane);

        for(int i=17;i<size-9;i+=9) inv.setItem(i,pane);
    }

    private static ItemStack createLanguageItem(Main plugin,String lang,String fileName){

        boolean active = plugin.getLanguageManager().getCurrentLanguage().equalsIgnoreCase(lang);

        ItemStack item = new ItemStack(getFlagMaterial(lang));
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName((active?"§a":"§b")+getLanguageName(lang));

        List<String> lore = new ArrayList<>();

        lore.add("§7File: §f"+fileName);
        lore.add(" ");

        if(active){
            lore.add("§aCurrent language");
        }else{
            lore.add("§eClick to select");
        }

        meta.setLore(lore);

        if(active){

            Enchantment glow = getSafeGlow();

            if(glow!=null){
                meta.addEnchant(glow,1,true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }

        item.setItemMeta(meta);

        return item;
    }

    private static Material getFlagMaterial(String lang){

        switch(lang.toLowerCase()){

            case "en": return Material.WHITE_BANNER;
            case "es": return Material.RED_BANNER;
            case "ru": return Material.BLUE_BANNER;
            case "zh": return Material.RED_BANNER;

            default: return Material.PAPER;
        }
    }

    private static String getLanguageName(String code){

        switch(code.toLowerCase()){

            case "en": return "English";
            case "es": return "Spanish";
            case "ru": return "Russian";
            case "zh": return "Chinese";

            default: return code.toUpperCase();
        }
    }

    private static ItemStack createPreviousItem(boolean enabled){

        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§cPrevious");

        meta.setLore(Collections.singletonList(
                enabled ? "§7Go to previous page" : "§8No previous page"
        ));

        item.setItemMeta(meta);

        return item;
    }

    private static ItemStack createNextItem(boolean enabled){

        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§aNext");

        meta.setLore(Collections.singletonList(
                enabled ? "§7Go to next page" : "§8No next page"
        ));

        item.setItemMeta(meta);

        return item;
    }

    private static ItemStack createInfoItem(Main plugin,int page,int maxPage,int total){

        ItemStack item = new ItemStack(Material.BOOK);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§eLanguage Browser");

        List<String> lore = new ArrayList<>();

        lore.add("§7Page: §f"+page+"§7/§f"+maxPage);
        lore.add("§7Languages: §f"+total);
        lore.add(" ");
        lore.add("§7Current Language:");
        lore.add("§a"+plugin.getLanguageManager().getCurrentLanguage().toUpperCase());

        meta.setLore(lore);

        item.setItemMeta(meta);

        return item;
    }

    private static ItemStack createConfirmItem(){

        ItemStack item = new ItemStack(Material.LIME_CONCRETE);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§aConfirm");

        meta.setLore(Collections.singletonList("§7Apply this language"));

        item.setItemMeta(meta);

        return item;
    }

    private static ItemStack createCancelItem(){

        ItemStack item = new ItemStack(Material.RED_CONCRETE);

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§cCancel");

        meta.setLore(Collections.singletonList("§7Go back"));

        item.setItemMeta(meta);

        return item;
    }

    private static ItemStack createConfirmInfoItem(String lang){

        ItemStack item = new ItemStack(getFlagMaterial(lang));

        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§eSelected: §f"+getLanguageName(lang));

        meta.setLore(Arrays.asList(
                "§7Are you sure you want",
                "§7to change language?"
        ));

        item.setItemMeta(meta);

        return item;
    }

    private static Enchantment getSafeGlow(){

        Enchantment[] values = Enchantment.values();

        return values.length>0 ? values[0] : null;
    }
}