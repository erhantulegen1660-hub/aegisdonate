package ru.aegis.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AegisCore extends JavaPlugin {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private StatsManager stats;
    private PackManager packs;
    private HudManager hud;
    private GameManager game;
    private PackListener packListener;
    private TitleManager titles;
    private CaseManager cases;
    private RankManager ranks;
    private DonateMenu donate;
    private BindManager binds;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        stats = new StatsManager(this);
        stats.load();

        packs = new PackManager(this);
        packs.load();

        titles = new TitleManager(this);
        cases = new CaseManager(this);
        ranks = new RankManager(this);
        donate = new DonateMenu(this);
        binds = new BindManager(this);
        binds.load();

        hud = new HudManager(this);
        game = new GameManager(this);
        game.reload();

        packListener = new PackListener(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(packListener, this);
        pm.registerEvents(game, this);
        pm.registerEvents(new CurrencyListener(this), this);
        pm.registerEvents(new RankListener(this), this);
        pm.registerEvents(cases, this);
        pm.registerEvents(donate, this);
        pm.registerEvents(binds, this);

        AegisCommand aegis = new AegisCommand(this);
        PluginCommand c1 = getCommand("aegis");
        if (c1 != null) {
            c1.setExecutor(aegis);
            c1.setTabCompleter(aegis);
        }
        AmethystCommand amethyst = new AmethystCommand(this);
        PluginCommand c2 = getCommand("amethyst");
        if (c2 != null) {
            c2.setExecutor(amethyst);
            c2.setTabCompleter(amethyst);
        }
        PluginCommand c3 = getCommand("donate");
        if (c3 != null) c3.setExecutor(new DonateCommand(this));
        SuffixCommand suffix = new SuffixCommand(this);
        PluginCommand c4 = getCommand("suffix");
        if (c4 != null) {
            c4.setExecutor(suffix);
            c4.setTabCompleter(suffix);
        }

        hud.start();
        ranks.start();

        getServer().getScheduler().runTaskTimer(this, () -> stats.save(), 6000L, 6000L);

        // players already online (after /reload)
        for (Player p : getServer().getOnlinePlayers()) {
            packListener.handleJoin(p);
            ranks.refresh(p);
        }
    }

    @Override
    public void onDisable() {
        if (ranks != null) ranks.stop();
        if (hud != null) hud.stop();
        if (game != null) game.shutdown();
        if (packs != null) packs.stop();
        if (binds != null) binds.save();
        if (stats != null) stats.save();
    }

    /** Reloads config.yml, HUD, game timeline, titles and rebuilds the resource pack zip. */
    public void reloadAll() {
        reloadConfig();
        packs.reload();
        titles.reload();
        hud.reloadSettings();
        game.reload();
    }

    public Component prefixed(String miniMessage) {
        return MM.deserialize(getConfig().getString("messages.prefix", "") + miniMessage);
    }

    public StatsManager stats() { return stats; }
    public PackManager packs() { return packs; }
    public HudManager hud() { return hud; }
    public GameManager game() { return game; }
    public PackListener packListener() { return packListener; }
    public TitleManager titles() { return titles; }
    public CaseManager cases() { return cases; }
    public RankManager ranks() { return ranks; }
    public DonateMenu donate() { return donate; }
    public BindManager binds() { return binds; }
}
