package ru.aegis.core;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right click with the physical amethyst = bank it (sneak = whole stack). */
public final class CurrencyListener implements Listener {

    private final AegisCore plugin;

    public CurrencyListener(AegisCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("currency.redeem-item", true)) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!Items.isAmethyst(hand)) return;

        e.setCancelled(true);
        int n = p.isSneaking() ? hand.getAmount() : 1;
        hand.setAmount(hand.getAmount() - n);
        plugin.stats().addAmethyst(p.getUniqueId(), n);
        plugin.hud().refresh(p);
        p.sendMessage(plugin.prefixed("<#E9D5FF>+" + n + " аметист(ов) на баланс. Всего: <#C084FC>"
                + plugin.stats().amethyst(p.getUniqueId())));
    }
}
