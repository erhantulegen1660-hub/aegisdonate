package ru.aegis.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cases: a physical item (model aegis:case). Right click opens it: one reward is picked by weight from
 * config cases.<id>.rewards and its commands are run from the console (%player% = opener).
 * While rewards are empty the case is not consumed.
 */
public final class CaseManager implements Listener {

    private final AegisCore plugin;

    public CaseManager(AegisCore plugin) {
        this.plugin = plugin;
    }

    public String displayName(String id) {
        return plugin.getConfig().getString("cases." + id + ".name", "<#C084FC>Кейс");
    }

    public void give(Player p, String id, int amount) {
        ItemStack it = Items.caseItem(id, AegisCore.MM.deserialize(displayName(id)), amount);
        p.getInventory().addItem(it).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String id = Items.caseId(hand);
        if (id == null) return;
        e.setCancelled(true);
        open(p, hand, id);
    }

    private void open(Player p, ItemStack hand, String id) {
        List<Map<?, ?>> rewards = plugin.getConfig().getMapList("cases." + id + ".rewards");
        if (rewards.isEmpty()) {
            p.sendMessage(plugin.prefixed("<gray>Содержимое этого кейса ещё не настроено. Сохраните его, скоро можно будет открыть."));
            return;
        }
        double total = 0;
        List<Double> weights = new ArrayList<>();
        for (Map<?, ?> r : rewards) {
            Object w = r.get("weight");
            double v = w instanceof Number n ? Math.max(0, n.doubleValue()) : 1;
            weights.add(v);
            total += v;
        }
        if (total <= 0) {
            p.sendMessage(plugin.prefixed("<red>Награды кейса настроены неверно (вес 0)."));
            return;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        Map<?, ?> pick = rewards.get(rewards.size() - 1);
        double acc = 0;
        for (int i = 0; i < rewards.size(); i++) {
            acc += weights.get(i);
            if (roll < acc) {
                pick = rewards.get(i);
                break;
            }
        }

        hand.setAmount(hand.getAmount() - 1);
        Object cmds = pick.get("commands");
        if (cmds instanceof List<?> list) {
            for (Object c : list) {
                String cmd = String.valueOf(c).replace("%player%", p.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
        Object nm = pick.get("name");
        Component name = AegisCore.MM.deserialize(nm == null ? "награда" : String.valueOf(nm));
        p.sendMessage(plugin.prefixed("<#E9D5FF>Из кейса выпало: ").append(name));
    }
}
