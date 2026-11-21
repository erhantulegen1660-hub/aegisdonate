package ru.aegis.core;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /suffix <MiniMessage> | preview <text> | reset */
public final class SuffixCommand implements CommandExecutor, TabCompleter {

    private final AegisCore plugin;

    public SuffixCommand(AegisCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        StatsManager.Stats s = plugin.stats().get(p.getUniqueId());

        if (args.length == 0) {
            p.sendMessage(plugin.prefixed("<gray>Токенов на титул: <white>" + s.titleTokens));
            plugin.titles().sendHelp(p);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("reset") && args.length == 1) {
            s.title = "";
            plugin.ranks().refresh(p);
            p.sendMessage(plugin.prefixed("<#E9D5FF>Титул убран. Токен не возвращается."));
            return true;
        }

        boolean preview = first.equals("preview");
        String raw = String.join(" ", preview ? java.util.Arrays.copyOfRange(args, 1, args.length) : args);
        TitleManager.Result res = plugin.titles().check(raw);
        if (!res.ok()) {
            p.sendMessage(plugin.prefixed("<red>" + res.error()));
            return true;
        }

        if (preview) {
            p.sendMessage(plugin.prefixed("<gray>Так будет выглядеть: ")
                    .append(Component.text(p.getName() + " ")).append(res.component()));
            return true;
        }

        if (s.titleTokens <= 0) {
            p.sendMessage(plugin.prefixed("<red>У вас нет токена на титул. <gray>Купить: <white>/donate"));
            return true;
        }
        s.titleTokens--;
        s.title = raw;
        plugin.ranks().refresh(p);
        p.sendMessage(plugin.prefixed("<#E9D5FF>Титул установлен: ")
                .append(Component.text(p.getName() + " ")).append(res.component()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("preview", "reset").stream()
                    .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
