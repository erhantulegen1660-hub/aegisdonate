package ru.aegis.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /amethyst [balance|give|take|set|item] */
public final class AmethystCommand implements CommandExecutor, TabCompleter {

    private final AegisCore plugin;

    public AmethystCommand(AegisCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean admin = sender.hasPermission("aegis.admin");
        String sub = args.length == 0 ? "balance" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "balance", "bal" -> {
                OfflinePlayer target;
                if (args.length >= 2 && admin) {
                    target = find(args[1]);
                    if (target == null) {
                        msg(sender, "<red>Игрок не найден.");
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    if (!sender.hasPermission("aegis.balance")) {
                        msg(sender, "<red>Нет прав.");
                        return true;
                    }
                    target = p;
                } else {
                    msg(sender, "<red>Укажите игрока: /amethyst balance <ник>");
                    return true;
                }
                msg(sender, "<#E9D5FF>Аметисты " + name(target) + ": <#C084FC>"
                        + plugin.stats().amethyst(target.getUniqueId()));
            }
            case "give", "take", "set" -> {
                if (!admin) {
                    msg(sender, "<red>Нет прав.");
                    return true;
                }
                if (args.length < 3) {
                    msg(sender, "<gray>Использование: /amethyst " + sub + " <ник> <количество>");
                    return true;
                }
                OfflinePlayer target = find(args[1]);
                if (target == null) {
                    msg(sender, "<red>Игрок не найден (должен хотя бы раз заходить на сервер).");
                    return true;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[2]);
                } catch (NumberFormatException ex) {
                    msg(sender, "<red>Количество должно быть числом.");
                    return true;
                }
                if (amount < 0) {
                    msg(sender, "<red>Количество не может быть отрицательным.");
                    return true;
                }
                switch (sub) {
                    case "give" -> plugin.stats().addAmethyst(target.getUniqueId(), amount);
                    case "take" -> plugin.stats().addAmethyst(target.getUniqueId(), -amount);
                    default -> plugin.stats().setAmethyst(target.getUniqueId(), amount);
                }
                Player online = target.getPlayer();
                if (online != null) plugin.hud().refresh(online);
                msg(sender, "<#E9D5FF>Баланс " + name(target) + ": <#C084FC>"
                        + plugin.stats().amethyst(target.getUniqueId()));
            }
            case "item" -> {
                if (!admin) {
                    msg(sender, "<red>Нет прав.");
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    msg(sender, "<red>Только для игроков.");
                    return true;
                }
                int amount = 1;
                if (args.length >= 2) {
                    try {
                        amount = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        // keep 1
                    }
                }
                p.getInventory().addItem(Items.amethyst(amount))
                        .values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                msg(sender, "<#E9D5FF>Выдан предмет «Аметист» x" + Math.max(1, Math.min(64, amount)));
            }
            default -> msg(sender, "<gray>/amethyst [balance|give|take|set|item]");
        }
        return true;
    }

    private OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private static String name(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : p.getUniqueId().toString();
    }

    private void msg(CommandSender s, String mm) {
        s.sendMessage(plugin.prefixed(mm));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("aegis.admin");
        if (args.length == 1) {
            out.add("balance");
            if (admin) out.addAll(List.of("give", "take", "set", "item"));
        } else if (args.length == 2 && admin && !args[0].equalsIgnoreCase("item")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
