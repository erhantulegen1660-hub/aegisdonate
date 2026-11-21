package ru.aegis.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds actions to items (right click) and to entities / NPCs (right click).
 * Actions: "donate" (opens the donate menu), "cmd:<command>" (player runs it),
 * "console:<command>" (console runs it, %player% = clicker).
 */
public final class BindManager implements Listener {

    private static final NamespacedKey ACTION_KEY = new NamespacedKey("aegis", "action");
    private static final long PENDING_MS = 30_000L;

    private record Pending(String action, long expires) {
    }

    private final AegisCore plugin;
    private final File file;
    private final Map<UUID, String> entityBinds = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public BindManager(AegisCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "binds.yml");
    }

    // ------------------------------------------------------------------ storage

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yc.getConfigurationSection("entities");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                entityBinds.put(UUID.fromString(k), sec.getString(k, ""));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : entityBinds.entrySet()) {
            yc.set("entities." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save binds.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ admin API

    /** Marks the item in hand. action == null clears it. */
    public boolean bindItem(Player p, String action) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir()) return false;
        ItemMeta meta = it.getItemMeta();
        if (action == null) {
            meta.getPersistentDataContainer().remove(ACTION_KEY);
        } else {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        }
        it.setItemMeta(meta);
        return true;
    }

    /** The next entity the player right-clicks gets bound (action == null: unbound). */
    public void bindNextEntity(Player p, String action) {
        pending.put(p.getUniqueId(), new Pending(action, System.currentTimeMillis() + PENDING_MS));
    }

    /** Spawns a motionless villager bound to the action. */
    public Villager spawnNpc(Player p, String action, String name) {
        Villager v = p.getWorld().spawn(p.getLocation(), Villager.class, e -> {
            e.setAI(false);
            e.setInvulnerable(true);
            e.setPersistent(true);
            e.setSilent(true);
            e.setCollidable(false);
            e.customName(AegisCore.MM.deserialize(name));
            e.setCustomNameVisible(true);
        });
        entityBinds.put(v.getUniqueId(), action);
        save();
        return v;
    }

    // ------------------------------------------------------------------ running

    public void run(Player p, String action) {
        if (action == null || action.isBlank()) return;
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < 300L) return;
        cooldown.put(p.getUniqueId(), now);

        if (action.equalsIgnoreCase("donate")) {
            plugin.donate().open(p);
        } else if (action.regionMatches(true, 0, "cmd:", 0, 4)) {
            p.performCommand(action.substring(4).trim());
        } else if (action.regionMatches(true, 0, "console:", 0, 8)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring(8).trim().replace("%player%", p.getName()));
        } else {
            plugin.getLogger().warning("Unknown bind action: " + action);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH)
    public void onItem(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        if (it.getType().isAir() || !it.hasItemMeta()) return;
        String action = it.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;
        e.setCancelled(true);
        run(e.getPlayer(), action);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Entity target = e.getRightClicked();

        Pending pd = pending.remove(p.getUniqueId());
        if (pd != null && System.currentTimeMillis() < pd.expires()) {
            e.setCancelled(true);
            if (pd.action() == null) {
                entityBinds.remove(target.getUniqueId());
                p.sendMessage(plugin.prefixed("<#E9D5FF>Привязка снята."));
            } else {
                entityBinds.put(target.getUniqueId(), pd.action());
                p.sendMessage(plugin.prefixed("<#E9D5FF>Существо привязано к действию <white>" + pd.action()));
            }
            save();
            return;
        }

        String action = entityBinds.get(target.getUniqueId());
        if (action == null) return;
        e.setCancelled(true);
        run(p, action);
    }

    public Component describe() {
        return Component.text("Привязано существ: " + entityBinds.size());
    }
}
