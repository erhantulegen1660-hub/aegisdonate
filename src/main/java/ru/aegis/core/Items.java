package ru.aegis.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Helpers for custom-model items from the resource pack. */
public final class Items {

    private static final NamespacedKey CURRENCY_KEY = new NamespacedKey("aegis", "currency");
    private static final NamespacedKey CASE_KEY = new NamespacedKey("aegis", "case");
    private static final NamespacedKey AMETHYST_MODEL = new NamespacedKey("aegis", "amethyst");
    private static final NamespacedKey CASE_MODEL = new NamespacedKey("aegis", "case");

    private Items() {
    }

    public static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** The physical amethyst (model aegis:amethyst from the resource pack). */
    public static ItemStack amethyst(int amount) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Аметист", TextColor.fromHexString("#C084FC"))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Валюта AEGIS", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("ПКМ - положить на баланс", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(AMETHYST_MODEL);
        meta.getPersistentDataContainer().set(CURRENCY_KEY, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isAmethyst(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(CURRENCY_KEY, PersistentDataType.BYTE);
    }

    /** A case item (model aegis:case) that remembers its case id. */
    public static ItemStack caseItem(String id, Component name, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(noItalic(name));
        meta.lore(List.of(noItalic(Component.text("ПКМ - открыть", NamedTextColor.GRAY))));
        meta.setItemModel(CASE_MODEL);
        meta.getPersistentDataContainer().set(CASE_KEY, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    /** @return case id or null when the item is not a case. */
    public static String caseId(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(CASE_KEY, PersistentDataType.STRING);
    }

    /** Menu icon with a model from the pack (model may be null). */
    public static ItemStack icon(String model, Material base, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(base);
        ItemMeta m = it.getItemMeta();
        m.displayName(noItalic(name));
        if (lore != null && !lore.isEmpty()) {
            m.lore(lore.stream().map(Items::noItalic).toList());
        }
        if (model != null) {
            NamespacedKey nk = NamespacedKey.fromString("aegis:" + model);
            if (nk != null) m.setItemModel(nk);
        }
        it.setItemMeta(m);
        return it;
    }

    /** Any item with a custom model, e.g. key "legendary/excalibur" -> aegis:legendary/excalibur. */
    public static ItemStack withModel(String key, Material base, int amount) {
        NamespacedKey nk = NamespacedKey.fromString("aegis:" + key.toLowerCase());
        if (nk == null) return null;
        ItemStack it = new ItemStack(base, Math.max(1, Math.min(base.getMaxStackSize(), amount)));
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(nk);
        it.setItemMeta(meta);
        return it;
    }
}
