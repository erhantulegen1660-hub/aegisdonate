package ru.aegis.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Battle royale (KB) timeline: MINING -> SHRINKING (any number of stages) -> PRE_DEATHMATCH -> DEATHMATCH.
 * Everything (names, durations, border sizes) is configured in config.yml -> game.timeline.
 */
public final class GameManager implements Listener {

    public enum PhaseType { MINING, SHRINKING, PRE_DEATHMATCH, DEATHMATCH }

    public record Phase(PhaseType type, String name, int duration, double borderSize, int shrinkSeconds) {
    }

    private final AegisCore plugin;
    private List<Phase> timeline = List.of();

    private boolean running;
    private int index;
    private int remaining;
    private BukkitTask task;

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();

    public GameManager(AegisCore plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------------------------- config

    public void reload() {
        List<Phase> list = new ArrayList<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("game.timeline")) {
            PhaseType type;
            try {
                type = PhaseType.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                plugin.getLogger().warning("game.timeline: unknown phase type '" + m.get("type") + "' - skipped");
                continue;
            }
            Object nm = m.get("name");
            String name = nm != null ? String.valueOf(nm) : type.name();
            list.add(new Phase(type, name,
                    (int) num(m, "duration", 60),
                    num(m, "border-size", 1000),
                    (int) num(m, "shrink-seconds", 0)));
        }
        timeline = List.copyOf(list);
    }

    private static double num(Map<?, ?> m, String key, double def) {
        Object o = m.get(key);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private World world() {
        String name = plugin.getConfig().getString("game.world", "world");
        World w = Bukkit.getWorld(name);
        return w != null ? w : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
    }

    // ------------------------------------------------------------------- control

    /** @return error message or null on success. */
    public String start() {
        if (running) return "Игра уже идёт.";
        if (timeline.isEmpty()) return "game.timeline в config.yml пуст.";
        World w = world();
        if (w == null) return "Мир игры не найден (game.world).";

        participants.clear();
        alive.clear();
        kills.clear();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            participants.add(p.getUniqueId());
            alive.add(p.getUniqueId());
            plugin.hud().setMode(p, HudMode.KB);
        }
        if (participants.isEmpty()) return "В мире «" + w.getName() + "» нет игроков.";

        FileConfiguration c = plugin.getConfig();
        WorldBorder b = w.getWorldBorder();
        b.setCenter(c.getDouble("game.center-x", 0), c.getDouble("game.center-z", 0));

        running = true;
        index = -1;
        Bukkit.getServer().sendMessage(plugin.prefixed(c.getString("messages.game-start", "")));
        nextPhase();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return null;
    }

    public void stop() {
        if (running) end(null);
    }

    /** Skips the current phase. */
    public void skip() {
        if (running) nextPhase();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running = false;
    }

    private void tick() {
        if (!running) return;
        remaining--;
        if (remaining <= 0) nextPhase();
    }

    private void nextPhase() {
        index++;
        if (index >= timeline.size()) {
            end(null);
            return;
        }
        Phase ph = timeline.get(index);
        remaining = ph.duration();

        World w = world();
        if (w != null) {
            WorldBorder b = w.getWorldBorder();
            if (ph.shrinkSeconds() > 0) {
                b.setSize(ph.borderSize(), (long) ph.shrinkSeconds());
            } else {
                b.setSize(ph.borderSize());
            }
        }

        FileConfiguration c = plugin.getConfig();
        Component title = AegisCore.MM.deserialize(c.getString("messages.phase-title", "<phase>"),
                Placeholder.unparsed("phase", ph.name()));
        Component sub = AegisCore.MM.deserialize(c.getString("messages.phase-subtitle", ""));
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(title, sub));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        }
        plugin.hud().refreshAll();
    }

    private void end(UUID winner) {
        if (!running) return;
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }

        FileConfiguration c = plugin.getConfig();
        long winReward = c.getLong("rewards.win", 0L);
        for (UUID id : participants) {
            StatsManager.Stats st = plugin.stats().get(id);
            if (winner != null) {
                if (id.equals(winner)) {
                    st.wins++;
                    long reward = plugin.ranks().winReward(id, winReward);
                    if (reward > 0) {
                        st.amethyst += reward;
                        Player wp = Bukkit.getPlayer(id);
                        if (wp != null) {
                            wp.sendMessage(plugin.prefixed("<#E9D5FF>За победу: <#C084FC>+" + reward + " аметист(ов)"));
                        }
                    }
                } else {
                    st.losses++;
                }
            }
        }

        boolean spectated = c.getBoolean("game.spectate-on-death", true);
        boolean toHub = c.getBoolean("game.hub-hud-after-end", true);
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (spectated && p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
            if (toHub) plugin.hud().setMode(p, HudMode.HUB);
        }

        World w = world();
        if (w != null) w.getWorldBorder().setSize(c.getDouble("game.border-after-end", 2000));

        if (winner != null) {
            String name = Bukkit.getOfflinePlayer(winner).getName();
            Bukkit.getServer().sendMessage(plugin.prefixed("") .append(AegisCore.MM.deserialize(
                    c.getString("messages.game-end-winner", "<winner>"),
                    Placeholder.unparsed("winner", name == null ? "?" : name))));
        } else {
            Bukkit.getServer().sendMessage(plugin.prefixed(c.getString("messages.game-end-nobody", "")));
        }
        participants.clear();
        alive.clear();
    }

    private void checkWin() {
        if (running && alive.size() <= 1) {
            end(alive.stream().findFirst().orElse(null));
        }
    }

    // -------------------------------------------------------------------- events

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        if (!running || !alive.remove(dead.getUniqueId())) return;

        Player killer = dead.getKiller();
        if (killer != null && participants.contains(killer.getUniqueId())) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
            StatsManager.Stats st = plugin.stats().get(killer.getUniqueId());
            st.kills++;
            long reward = plugin.getConfig().getLong("rewards.kill", 0L);
            if (reward > 0) st.amethyst += reward;
        }

        if (plugin.getConfig().getBoolean("game.spectate-on-death", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (dead.isOnline()) dead.setGameMode(GameMode.SPECTATOR);
            }, 2L);
        }
        checkWin();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (running && alive.remove(e.getPlayer().getUniqueId())) checkWin();
    }

    // ---------------------------------------------------------------- HUD / info

    public boolean isRunning() {
        return running;
    }

    public PhaseType currentType() {
        return running && index >= 0 && index < timeline.size() ? timeline.get(index).type() : null;
    }

    public String status() {
        if (!running) return "Игра не запущена.";
        Phase ph = timeline.get(index);
        return "Фаза " + (index + 1) + "/" + timeline.size() + ": " + ph.name() + " (" + ph.type() + "), осталось "
                + format(remaining) + ", живых: " + alive.size();
    }

    public void fillTokens(Player p, Map<String, String> t) {
        t.put("alive", running ? String.valueOf(alive.size()) : "-");
        t.put("kills", String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)));
        t.put("phase_name", running && index >= 0 && index < timeline.size()
                ? AegisCore.MM.escapeTags(timeline.get(index).name()) : "Ожидание");
        t.put("next_change", running ? format(remaining) : "--:--");
        World w = world();
        t.put("border", w != null ? String.valueOf((int) Math.round(w.getWorldBorder().getSize())) : "-");
    }

    private static String format(int seconds) {
        int s = Math.max(0, seconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
