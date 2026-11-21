package ru.aegis.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** VIP / VIP+: badges, win rewards, periodic cases, expiry. */
public final class RankManager {

    private static final Key FONT = Key.key("aegis", "hud");
    private static final long DAY_MS = 86_400_000L;

    private final AegisCore plugin;
    private BukkitTask task;

    public RankManager(AegisCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) check(p);
        }, 200L, 1200L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // --------------------------------------------------------------------- queries

    /** Current rank (expired ranks count as NONE). */
    public Rank rank(UUID id) {
        StatsManager.Stats s = plugin.stats().get(id);
        Rank r = Rank.fromKey(s.rank);
        if (r != Rank.NONE && s.rankExpire > 0 && System.currentTimeMillis() >= s.rankExpire) return Rank.NONE;
        return r;
    }

    /** True for VIP and VIP+ (use this in event plugins as the event pass). */
    public boolean isVip(UUID id) {
        return rank(id) != Rank.NONE;
    }

    public boolean joinFull(Rank r) {
        return r != Rank.NONE && plugin.getConfig().getBoolean("ranks." + r.key() + ".join-full", true);
    }

    public long winReward(UUID id, long base) {
        Rank r = rank(id);
        if (r == Rank.NONE) return base;
        return Math.max(base, plugin.getConfig().getLong("ranks." + r.key() + ".win-reward", 0L));
    }

    public int caseDays(Rank r) {
        return Math.max(1, plugin.getConfig().getInt("ranks." + r.key() + ".case-days", 14));
    }

    public String caseId(Rank r) {
        return plugin.getConfig().getString("ranks." + r.key() + ".case-id", r.key());
    }

    public TextColor nameColor(Rank r) {
        if (r == Rank.NONE) return NamedTextColor.WHITE;
        TextColor c = TextColor.fromHexString(plugin.getConfig().getString("ranks." + r.key() + ".color", "#FFFFFF"));
        return c != null ? c : NamedTextColor.WHITE;
    }

    public Component badge(Rank r) {
        if (r == Rank.NONE) return Component.empty();
        return Component.text(String.valueOf(r.glyph())).font(FONT);
    }

    public String formatDate(long ms) {
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("hud.timezone", "Europe/Moscow"));
        } catch (Exception ex) {
            zone = ZoneId.of("Europe/Moscow");
        }
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone).format(Instant.ofEpochMilli(ms));
    }

    // -------------------------------------------------------------------- changes

    /** @param days 0 = forever */
    public void setRank(UUID id, Rank r, int days) {
        StatsManager.Stats s = plugin.stats().get(id);
        Rank old = Rank.fromKey(s.rank);
        long now = System.currentTimeMillis();
        s.rank = r.key();
        s.rankExpire = (r != Rank.NONE && days > 0) ? now + days * DAY_MS : 0L;
        if (r == Rank.NONE) {
            s.nextCase = 0L;
        } else {
            long next = now + caseDays(r) * DAY_MS;
            if (old != Rank.NONE && s.nextCase > 0) next = Math.min(next, s.nextCase);
            s.nextCase = next;
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) refresh(p);
    }

    /** Updates the tab-list name (badge + name + title). */
    public void refresh(Player p) {
        Rank r = rank(p.getUniqueId());
        Component name = Component.empty();
        if (r != Rank.NONE) name = name.append(badge(r)).append(Component.space());
        name = name.append(Component.text(p.getName(), nameColor(r)));
        Component title = plugin.titles().render(plugin.stats().get(p.getUniqueId()).title);
        if (!title.equals(Component.empty())) name = name.append(Component.space()).append(title);
        p.playerListName(name);
        plugin.hud().refresh(p);
    }

    /** Expiry check and periodic case delivery for one online player. */
    public void check(Player p) {
        StatsManager.Stats s = plugin.stats().get(p.getUniqueId());
        Rank r = Rank.fromKey(s.rank);
        if (r == Rank.NONE) return;
        long now = System.currentTimeMillis();

        if (s.rankExpire > 0 && now >= s.rankExpire) {
            s.rank = Rank.NONE.key();
            s.rankExpire = 0L;
            s.nextCase = 0L;
            refresh(p);
            p.sendMessage(plugin.prefixed("<gray>Срок вашего ранга <white>" + r.label() + "</white> закончился."));
            return;
        }
        if (s.nextCase <= 0) {
            s.nextCase = now + caseDays(r) * DAY_MS;
            return;
        }
        if (now >= s.nextCase) {
            plugin.cases().give(p, caseId(r), 1);
            s.nextCase = now + caseDays(r) * DAY_MS;
            p.sendMessage(plugin.prefixed("<#E9D5FF>Вы получили кейс за ранг <white>" + r.label() + "</white>! ПКМ - открыть."));
        }
    }
}
