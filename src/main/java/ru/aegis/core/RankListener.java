package ru.aegis.core;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

/** Chat format with badge and title, tab list, joining a full server for VIPs. */
public final class RankListener implements Listener {

    private final AegisCore plugin;

    public RankListener(AegisCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;
        e.renderer(ChatRenderer.viewerUnaware((source, sourceName, message) -> {
            Rank r = plugin.ranks().rank(source.getUniqueId());
            Component out = Component.empty();
            if (r != Rank.NONE) out = out.append(plugin.ranks().badge(r)).append(Component.space());
            out = out.append(Component.text(source.getName(), plugin.ranks().nameColor(r)));
            Component title = plugin.titles().render(plugin.stats().get(source.getUniqueId()).title);
            if (!title.equals(Component.empty())) out = out.append(Component.space()).append(title);
            out = out.append(AegisCore.MM.deserialize(
                    plugin.getConfig().getString("chat.separator", " <dark_gray>»</dark_gray> ")));
            return out.append(message);
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.ranks().refresh(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.ranks().check(p);
        }, 40L);
    }

    /** VIP and VIP+ can join even when the server is full. */
    @EventHandler
    public void onLogin(PlayerLoginEvent e) {
        if (e.getResult() != PlayerLoginEvent.Result.KICK_FULL) return;
        Rank r = plugin.ranks().rank(e.getPlayer().getUniqueId());
        if (plugin.ranks().joinFull(r)) e.allow();
    }
}
