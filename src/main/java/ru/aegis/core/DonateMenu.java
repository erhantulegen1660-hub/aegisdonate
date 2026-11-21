package ru.aegis.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /donate - menu with VIP, VIP+, title token, personal status and instructions. */
public final class DonateMenu implements Listener {

    private static final int SLOT_VIP = 11;
    private static final int SLOT_VIPPLUS = 13;
    private static final int SLOT_TOKEN = 15;
    private static final int SLOT_STATUS = 29;
    private static final int SLOT_HELP = 31;
    private static final int SLOT_BALANCE = 33;
    private static final int SLOT_CLOSE = 49;

    private static final class Holder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final AegisCore plugin;

    public DonateMenu(AegisCore plugin) {
        this.plugin = plugin;
    }

    private List<Component> lines(String path, String... extra) {
        List<Component> out = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList(path)) {
            for (int i = 0; i + 1 < extra.length; i += 2) {
                s = s.replace(extra[i], extra[i + 1]);
            }
            out.add(AegisCore.MM.deserialize(s));
        }
        return out;
    }

    private Component text(String path, String def) {
        return AegisCore.MM.deserialize(plugin.getConfig().getString(path, def));
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Component title = text("donate.title", "<#C084FC>AEGIS - Донат");
        Inventory inv = org.bukkit.Bukkit.createInventory(holder, 54, title);
        holder.inv = inv;

        ItemStack filler = Items.icon(null, Material.PURPLE_STAINED_GLASS_PANE, Component.text(" "), null);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_VIP, Items.icon("vip", Material.PAPER,
                text("donate.vip.title", "<#4ADE80><bold>VIP"), lines("donate.vip.lore")));
        inv.setItem(SLOT_VIPPLUS, Items.icon("vipplus", Material.PAPER,
                text("donate.vipplus.title", "<#FBBF24><bold>VIP+"), lines("donate.vipplus.lore")));
        inv.setItem(SLOT_TOKEN, Items.icon("title_token", Material.PAPER,
                text("donate.token.title", "<#C084FC><bold>Токен на титул"),
                lines("donate.token.lore", "%max%", String.valueOf(plugin.titles().maxLength()),
                        "%url%", plugin.titles().editorUrl())));

        inv.setItem(SLOT_STATUS, status(p));
        inv.setItem(SLOT_HELP, Items.icon(null, Material.BOOK,
                text("donate.help.title", "<#E9D5FF>Как сделать титул"),
                lines("donate.help.lore", "%max%", String.valueOf(plugin.titles().maxLength()),
                        "%url%", plugin.titles().editorUrl())));
        inv.setItem(SLOT_BALANCE, Items.icon("amethyst", Material.PAPER,
                text("donate.balance.title", "<#C084FC>Аметисты"),
                List.of(AegisCore.MM.deserialize("<gray>Ваш баланс: <#E9D5FF>"
                        + plugin.stats().amethyst(p.getUniqueId())))));
        inv.setItem(SLOT_CLOSE, Items.icon(null, Material.BARRIER,
                Component.text("Закрыть", NamedTextColor.RED), null));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
    }

    private ItemStack status(Player p) {
        UUID id = p.getUniqueId();
        StatsManager.Stats s = plugin.stats().get(id);
        Rank r = plugin.ranks().rank(id);
        List<Component> lore = new ArrayList<>();
        lore.add(AegisCore.MM.deserialize("<gray>Ранг: <white>" + r.label()));
        if (r != Rank.NONE) {
            lore.add(AegisCore.MM.deserialize("<gray>Действует: <white>"
                    + (s.rankExpire > 0 ? "до " + plugin.ranks().formatDate(s.rankExpire) : "навсегда")));
            if (s.nextCase > 0) {
                lore.add(AegisCore.MM.deserialize("<gray>Следующий кейс: <white>"
                        + plugin.ranks().formatDate(s.nextCase)));
            }
        }
        lore.add(AegisCore.MM.deserialize("<gray>Токенов на титул: <white>" + s.titleTokens));
        Component title = plugin.titles().render(s.title);
        lore.add(title.equals(Component.empty())
                ? AegisCore.MM.deserialize("<gray>Титул: <dark_gray>нет")
                : AegisCore.MM.deserialize("<gray>Титул: ").append(title));
        return Items.icon(null, Material.NETHER_STAR,
                text("donate.status.title", "<#E9D5FF>Мой статус"), lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        switch (e.getSlot()) {
            case SLOT_VIP, SLOT_VIPPLUS -> {
                p.closeInventory();
                p.sendMessage(plugin.prefixed(plugin.getConfig().getString("donate.buy-hint",
                        "<gray>Покупка скоро появится у NPC.")));
            }
            case SLOT_TOKEN -> {
                p.closeInventory();
                p.sendMessage(plugin.prefixed(plugin.getConfig().getString("donate.buy-hint",
                        "<gray>Покупка скоро появится у NPC.")));
                plugin.titles().sendHelp(p);
            }
            case SLOT_HELP -> {
                p.closeInventory();
                plugin.titles().sendHelp(p);
            }
            case SLOT_CLOSE -> p.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
