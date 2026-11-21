package ru.aegis.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stores amethysts, stats, rank, title and case timers (plugins/AegisCore/data.yml). */
public final class StatsManager {

    public static final class Stats {
        public volatile long amethyst;
        public volatile int wins;
        public volatile int losses;
        public volatile int kills;
        /** Rank key: none / vip / vipplus. */
        public volatile String rank = "none";
        /** Epoch millis when the rank ends, 0 = never. */
        public volatile long rankExpire;
        /** Custom title as MiniMessage text, empty = none. */
        public volatile String title = "";
        /** Unused title tokens. */
        public volatile int titleTokens;
        /** Epoch millis when the next rank case is due, 0 = not scheduled. */
        public volatile long nextCase;
    }

    private final AegisCore plugin;
    private final File file;
    private final Map<UUID, Stats> map = new ConcurrentHashMap<>();

    public StatsManager(AegisCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yc.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Stats s = new Stats();
                s.amethyst = root.getLong(key + ".amethyst", 0L);
                s.wins = root.getInt(key + ".wins", 0);
                s.losses = root.getInt(key + ".losses", 0);
                s.kills = root.getInt(key + ".kills", 0);
                s.rank = root.getString(key + ".rank", "none");
                s.rankExpire = root.getLong(key + ".rank-expire", 0L);
                s.title = root.getString(key + ".title", "");
                s.titleTokens = root.getInt(key + ".title-tokens", 0);
                s.nextCase = root.getLong(key + ".next-case", 0L);
                map.put(id, s);
            } catch (IllegalArgumentException ignored) {
                // bad uuid key - skip
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Map.Entry<UUID, Stats> e : map.entrySet()) {
            String base = "players." + e.getKey();
            Stats s = e.getValue();
            yc.set(base + ".amethyst", s.amethyst);
            yc.set(base + ".wins", s.wins);
            yc.set(base + ".losses", s.losses);
            yc.set(base + ".kills", s.kills);
            yc.set(base + ".rank", s.rank);
            yc.set(base + ".rank-expire", s.rankExpire);
            yc.set(base + ".title", s.title);
            yc.set(base + ".title-tokens", s.titleTokens);
            yc.set(base + ".next-case", s.nextCase);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public Stats get(UUID id) {
        return map.computeIfAbsent(id, k -> new Stats());
    }

    public long amethyst(UUID id) {
        return get(id).amethyst;
    }

    public void setAmethyst(UUID id, long value) {
        get(id).amethyst = Math.max(0L, value);
    }

    public void addAmethyst(UUID id, long delta) {
        Stats s = get(id);
        s.amethyst = Math.max(0L, s.amethyst + delta);
    }
}
