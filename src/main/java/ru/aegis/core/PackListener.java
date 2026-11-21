package ru.aegis.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the resource pack on join, kicks players who refuse or fail to load it
 * and freezes them until it is loaded. The HUD is shown only after the pack is loaded.
 */
public final class PackListener implements Listener {

    private final AegisCore plugin;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public PackListener(AegisCore plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(Player p) {
        return pending.contains(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        handleJoin(e.getPlayer());
    }

    public void handleJoin(Player p) {
        FileConfiguration c = plugin.getConfig();
        boolean packOn = c.getBoolean("resourcepack.enabled", true) && plugin.packs().isReady();
        String bypass = c.getString("resourcepack.bypass-permission", "aegis.bypass");
        boolean bypassed = bypass != null && !bypass.isEmpty() && p.hasPermission(bypass);

        if (!packOn || bypassed) {
            plugin.hud().show(p);
            return;
        }

        if (c.getBoolean("resourcepack.required", true)) {
            pending.add(p.getUniqueId());
            long ticks = Math.max(5, c.getInt("resourcepack.timeout-seconds", 60)) * 20L;
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pending.contains(id)) {
                    Player q = Bukkit.getPlayer(id);
                    if (q != null) kick(q, "resourcepack.kick-timeout");
                }
            }, ticks);
        }
        plugin.packs().send(p);
    }

    private void kick(Player p, String path) {
        pending.remove(p.getUniqueId());
        Component msg = AegisCore.MM.deserialize(plugin.getConfig().getString(path, "Resource pack required."));
        p.kick(msg);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        if (!PackManager.PACK_ID.equals(e.getID())) return;
        boolean required = plugin.getConfig().getBoolean("resourcepack.required", true);
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                pending.remove(p.getUniqueId());
                plugin.hud().show(p);
            }
            case DECLINED -> {
                if (required) kick(p, "resourcepack.kick-declined");
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                if (required) kick(p, "resourcepack.kick-failed");
            }
            default -> {
                // ACCEPTED / DOWNLOADED / DISCARDED - nothing to do
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        plugin.hud().remove(e.getPlayer());
    }

    // ---- freeze while the pack is loading -------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        if (isPending(e.getPlayer()) && e.hasChangedPosition()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (isPending(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (isPending(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (isPending(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (isPending(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (isPending(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isPending(p)) e.setCancelled(true);
    }
}
