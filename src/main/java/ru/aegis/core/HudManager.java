package ru.aegis.core;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Right-hand sidebar HUD. Every player has a private scoreboard; lines are MiniMessage
 * strings from config.yml, icons are glyphs of the "aegis:hud" font from the resource pack.
 */
public final class HudManager {

    private static final Key FONT = Key.key("aegis", "hud");
    private static final char[] TITLE_LETTERS = {'', '', '', '', ''}; // A E G I S
    private static final char SPACER = '';
    private static final int MAX_LINES = 15;

    private static final Map<String, Character> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("ping", '');
        ICONS.put("gem", '');
        ICONS.put("amethyst", '');
        ICONS.put("user", '');
        ICONS.put("calendar", '');
        ICONS.put("clock", '');
        ICONS.put("hourglass", '');
        ICONS.put("trophy", '');
        ICONS.put("skull", '');
        ICONS.put("heart", '');
        ICONS.put("swords", '');
        ICONS.put("flag", '');
        ICONS.put("shield", '');
        ICONS.put("vip", '');
        ICONS.put("vipplus", '');
    }

    private static final class State {
        final Scoreboard board;
        final Objective objective;
        List<Component> last = new ArrayList<>();

        State(Scoreboard board, Objective objective) {
            this.board = board;
            this.objective = objective;
        }
    }

    private final AegisCore plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, HudMode> modes = new HashMap<>();

    private BukkitTask task;
    private int interval = 20;
    private int accumulated = 0;

    private boolean enabled = true;
    private boolean animate = true;
    private long periodMs = 3200;
    private List<TextColor> palette = List.of(TextColor.color(0xA855F7));
    private ZoneId zone = ZoneId.of("Europe/Moscow");
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

    public HudManager(AegisCore plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    // ------------------------------------------------------------------ lifecycle

    public void reloadSettings() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("hud.enabled", true);
        animate = c.getBoolean("hud.title-animation.enabled", true);
        periodMs = Math.max(500L, c.getLong("hud.title-animation.period-ms", 3200L));
        interval = animate ? Math.max(1, c.getInt("hud.title-animation.update-ticks", 3)) : 20;

        List<TextColor> colors = new ArrayList<>();
        for (String hex : c.getStringList("hud.title-animation.colors")) {
            TextColor tc = TextColor.fromHexString(hex.trim());
            if (tc != null) colors.add(tc);
        }
        palette = colors.isEmpty() ? List.of(TextColor.color(0xA855F7)) : colors;

        try {
            zone = ZoneId.of(c.getString("hud.timezone", "Europe/Moscow"));
        } catch (Exception ex) {
            zone = ZoneId.of("Europe/Moscow");
        }

        if (task != null) {
            task.cancel();
            task = null;
            start();
        }
        refreshAll();
    }

    public void start() {
        if (!enabled || task != null) return;
        accumulated = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (Bukkit.getScoreboardManager() != null) {
            for (UUID id : states.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        states.clear();
    }

    private void tick() {
        accumulated += interval;
        boolean full = accumulated >= 20;
        if (full) accumulated = 0;
        for (Map.Entry<UUID, State> en : states.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) continue;
            State s = en.getValue();
            if (animate || full) s.objective.displayName(title());
            if (full) renderLines(p, s);
        }
    }

    // ------------------------------------------------------------------- players

    public void show(Player p) {
        if (!enabled || Bukkit.getScoreboardManager() == null) return;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("aegis", Criteria.DUMMY, title());
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        State s = new State(board, obj);
        states.put(p.getUniqueId(), s);
        p.setScoreboard(board);
        renderLines(p, s);
        start();
    }

    public void remove(Player p) {
        states.remove(p.getUniqueId());
        modes.remove(p.getUniqueId());
    }

    public HudMode mode(Player p) {
        return modes.getOrDefault(p.getUniqueId(), HudMode.HUB);
    }

    public void setMode(Player p, HudMode mode) {
        modes.put(p.getUniqueId(), mode);
        refresh(p);
    }

    public void refresh(Player p) {
        State s = states.get(p.getUniqueId());
        if (s != null) renderLines(p, s);
    }

    public void refreshAll() {
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) refresh(p);
        }
    }

    // ----------------------------------------------------------------- rendering

    private void renderLines(Player p, State s) {
        List<String> raw = rawLines(p);
        int n = Math.min(raw.size(), MAX_LINES);
        Map<String, String> tokens = tokens(p);

        List<Component> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(parse(raw.get(i), tokens));

        for (int i = 0; i < n; i++) {
            Score sc = s.objective.getScore("l" + i);
            sc.setScore(n - i);
            if (i >= s.last.size() || !lines.get(i).equals(s.last.get(i))) {
                sc.customName(lines.get(i));
            }
        }
        for (int i = n; i < s.last.size(); i++) {
            s.board.resetScores("l" + i);
        }
        s.last = lines;
    }

    private List<String> rawLines(Player p) {
        FileConfiguration c = plugin.getConfig();
        if (mode(p) == HudMode.HUB) return c.getStringList("hud.hub");
        GameManager.PhaseType type = plugin.game().currentType();
        if (type != null && c.isList("hud.kb." + type.name())) {
            return c.getStringList("hud.kb." + type.name());
        }
        return c.getStringList("hud.kb.default");
    }

    private Map<String, String> tokens(Player p) {
        Map<String, String> t = new HashMap<>();
        StatsManager.Stats st = plugin.stats().get(p.getUniqueId());
        ZonedDateTime now = ZonedDateTime.now(zone);
        t.put("nick", AegisCore.MM.escapeTags(p.getName()));
        t.put("ping", String.valueOf(p.getPing()));
        t.put("amethyst", String.valueOf(st.amethyst));
        t.put("wins", String.valueOf(st.wins));
        t.put("losses", String.valueOf(st.losses));
        t.put("total_kills", String.valueOf(st.kills));
        t.put("date", dateFmt.format(now));
        t.put("time", timeFmt.format(now));
        t.put("restart_in", restartIn(now));
        t.put("ip", plugin.getConfig().getString("server-ip", "aegis.gamepvp.ru"));
        t.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        Rank rk = plugin.ranks().rank(p.getUniqueId());
        t.put("rank", rk == Rank.NONE ? "Игрок" : rk.label());
        t.put("rank_badge", rk == Rank.NONE ? "" : "<font:aegis:hud>" + rk.glyph() + "</font>");
        plugin.game().fillTokens(p, t);
        return t;
    }

    private Component parse(String raw, Map<String, String> tokens) {
        String s = raw == null ? "" : raw;
        for (Map.Entry<String, Character> e : ICONS.entrySet()) {
            s = s.replace("%icon_" + e.getKey() + "%",
                    "<font:aegis:hud><white>" + e.getValue() + "</white></font>");
        }
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue());
        }
        if (s.isBlank()) return Component.text(" ");
        try {
            return AegisCore.MM.deserialize(s);
        } catch (Exception ex) {
            return Component.text(s);
        }
    }

    private String restartIn(ZonedDateTime now) {
        String s = plugin.getConfig().getString("hud.restart-time", "");
        if (s == null || s.isBlank()) return "-";
        try {
            LocalTime rt = LocalTime.parse(s.trim());
            ZonedDateTime next = now.with(rt);
            if (!next.isAfter(now)) next = next.plusDays(1);
            Duration d = Duration.between(now, next);
            long h = d.toHours();
            long m = d.toMinutesPart();
            return h > 0 ? h + " ч " + m + " мин" : m + " мин";
        } catch (Exception ex) {
            return "-";
        }
    }

    // --------------------------------------------------------------------- title

    private Component title() {
        double t = (System.currentTimeMillis() % periodMs) / (double) periodMs;
        Component c = Component.empty();
        for (int i = 0; i < TITLE_LETTERS.length; i++) {
            double pos = (t + i * 0.12) % 1.0;
            c = c.append(Component.text(TITLE_LETTERS[i], colorAt(pos)).font(FONT));
            if (i < TITLE_LETTERS.length - 1) {
                c = c.append(Component.text(SPACER).font(FONT));
            }
        }
        return c;
    }

    private TextColor colorAt(double pos) {
        int n = palette.size();
        if (n == 1) return palette.get(0);
        double scaled = pos * n;
        int i = ((int) Math.floor(scaled)) % n;
        float f = (float) (scaled - Math.floor(scaled));
        return TextColor.lerp(f, palette.get(i), palette.get((i + 1) % n));
    }
}
