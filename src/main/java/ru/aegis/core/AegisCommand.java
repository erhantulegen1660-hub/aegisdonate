package ru.aegis.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /aegis help | reload | hud | game | pack | item | rank | token | title | case | bind | npc */
public final class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisCore plugin;

    public AegisCommand(AegisCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "<#E9D5FF>Конфиг перезагружен, ресурспак пересобран.");
            }
            case "hud" -> hud(sender, args);
            case "pack" -> pack(sender, args);
            case "item" -> item(sender, args);
            case "game" -> game(sender, args);
            case "rank" -> rank(sender, args);
            case "token" -> token(sender, args);
            case "title" -> title(sender, args);
            case "case" -> caseCmd(sender, args);
            case "bind" -> bind(sender, args);
            case "npc" -> npc(sender, args);
            default -> help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------------ hud

    private void hud(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<gray>/aegis hud <hub|kb> [ник|*]");
            return;
        }
        HudMode mode;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "hub" -> mode = HudMode.HUB;
            case "kb" -> mode = HudMode.KB;
            default -> {
                msg(sender, "<red>Режим: hub или kb.");
                return;
            }
        }
        List<Player> targets = new ArrayList<>();
        if (args.length >= 3) {
            if (args[2].equals("*")) {
                targets.addAll(Bukkit.getOnlinePlayers());
            } else {
                Player t = Bukkit.getPlayerExact(args[2]);
                if (t == null) {
                    msg(sender, "<red>Игрок не в сети.");
                    return;
                }
                targets.add(t);
            }
        } else if (sender instanceof Player p) {
            targets.add(p);
        } else {
            msg(sender, "<red>Укажите игрока или *.");
            return;
        }
        for (Player t : targets) plugin.hud().setMode(t, mode);
        msg(sender, "<#E9D5FF>HUD «" + mode.name() + "» включён для " + targets.size() + " игрок(ов).");
    }

    // ----------------------------------------------------------------------- pack

    private void pack(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "rebuild" -> {
                plugin.packs().rebuild();
                msg(sender, "<#E9D5FF>pack.zip пересобран. sha1: <gray>" + plugin.packs().sha1Hex());
                msg(sender, "<gray>Чтобы игроки получили новый пак: <white>/aegis pack send *");
            }
            case "send" -> {
                List<Player> targets = new ArrayList<>();
                if (args.length >= 3 && args[2].equals("*")) {
                    targets.addAll(Bukkit.getOnlinePlayers());
                } else if (args.length >= 3) {
                    Player t = Bukkit.getPlayerExact(args[2]);
                    if (t != null) targets.add(t);
                } else if (sender instanceof Player p) {
                    targets.add(p);
                }
                if (targets.isEmpty()) {
                    msg(sender, "<red>Нет получателей.");
                    return;
                }
                for (Player t : targets) plugin.packs().send(t);
                msg(sender, "<#E9D5FF>Ресурспак отправлен: " + targets.size());
            }
            case "reset" -> {
                int n = plugin.packs().extractDefaults(true);
                plugin.packs().rebuild();
                msg(sender, "<#E9D5FF>Стандартные файлы пака восстановлены (" + n + "), твои файлы не тронуты.");
            }
            default -> {
                msg(sender, "<#E9D5FF>Ссылка: <gray>" + plugin.packs().url());
                msg(sender, "<#E9D5FF>sha1: <gray>" + plugin.packs().sha1Hex());
                msg(sender, "<#E9D5FF>Папка: <gray>" + plugin.packs().folder().getPath());
            }
        }
    }

    // ----------------------------------------------------------------------- item

    private void item(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "<red>Только для игроков.");
            return;
        }
        if (args.length < 2) {
            msg(sender, "<gray>/aegis item <модель> [материал] [количество]");
            msg(sender, "<gray>Пример: /aegis item legendary/excalibur NETHERITE_SWORD");
            return;
        }
        String key = args[1];
        if (key.equalsIgnoreCase("amethyst") && args.length == 2) {
            p.getInventory().addItem(Items.amethyst(1));
            return;
        }
        Material base = Material.PAPER;
        if (args.length >= 3) {
            Material m = Material.matchMaterial(args[2]);
            if (m == null || m.isAir() || !m.isItem()) {
                msg(sender, "<red>Неизвестный материал: " + args[2]);
                return;
            }
            base = m;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                // keep 1
            }
        }
        ItemStack it = Items.withModel(key, base, amount);
        if (it == null) {
            msg(sender, "<red>Некорректный ключ модели (только a-z, 0-9, _ - . /).");
            return;
        }
        p.getInventory().addItem(it).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        msg(sender, "<#E9D5FF>Выдан предмет с моделью <gray>aegis:" + key.toLowerCase(Locale.ROOT));
    }

    // ----------------------------------------------------------------------- game

    private void game(CommandSender sender, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "start" -> {
                String err = plugin.game().start();
                if (err != null) msg(sender, "<red>" + err);
            }
            case "stop" -> {
                plugin.game().stop();
                msg(sender, "<#E9D5FF>Игра остановлена.");
            }
            case "skip" -> {
                plugin.game().skip();
                msg(sender, "<#E9D5FF>Фаза пропущена.");
            }
            default -> msg(sender, "<#E9D5FF>" + plugin.game().status());
        }
    }

    // ----------------------------------------------------------------------- rank

    private void rank(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "<gray>/aegis rank give <ник> <vip|vipplus|none> [дней]");
            msg(sender, "<gray>/aegis rank info <ник>");
            return;
        }
        OfflinePlayer t = find(args[2]);
        if (t == null) {
            msg(sender, "<red>Игрок не найден (должен хотя бы раз заходить на сервер).");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("info")) {
            StatsManager.Stats s = plugin.stats().get(t.getUniqueId());
            Rank r = plugin.ranks().rank(t.getUniqueId());
            msg(sender, "<#E9D5FF>" + name(t) + ": ранг <white>" + r.label()
                    + "<#E9D5FF>, действует <white>"
                    + (r == Rank.NONE ? "-" : s.rankExpire > 0 ? "до " + plugin.ranks().formatDate(s.rankExpire) : "навсегда")
                    + "<#E9D5FF>, токенов <white>" + s.titleTokens);
            return;
        }
        if (!sub.equals("give") || args.length < 4) {
            msg(sender, "<gray>/aegis rank give <ник> <vip|vipplus|none> [дней]");
            return;
        }
        Rank r = Rank.fromKey(args[3]);
        if (r == Rank.NONE && !args[3].equalsIgnoreCase("none")) {
            msg(sender, "<red>Ранг: vip, vipplus или none.");
            return;
        }
        int days = 0;
        if (args.length >= 5) {
            try {
                days = Math.max(0, Integer.parseInt(args[4]));
            } catch (NumberFormatException ex) {
                msg(sender, "<red>Дни должны быть числом.");
                return;
            }
        }
        plugin.ranks().setRank(t.getUniqueId(), r, days);
        msg(sender, "<#E9D5FF>" + name(t) + ": ранг <white>" + r.label()
                + (r != Rank.NONE ? (days > 0 ? " на " + days + " дн." : " навсегда") : ""));
        Player online = t.getPlayer();
        if (online != null && r != Rank.NONE) {
            online.sendMessage(plugin.prefixed("<#E9D5FF>Вам выдан ранг <white>" + r.label() + "</white>! Подробности: /donate"));
        }
    }

    // ---------------------------------------------------------------------- token

    private void token(CommandSender sender, String[] args) {
        if (args.length < 4) {
            msg(sender, "<gray>/aegis token <give|take> <ник> <количество>  <dark_gray>(токены на титул)");
            return;
        }
        OfflinePlayer t = find(args[2]);
        if (t == null) {
            msg(sender, "<red>Игрок не найден.");
            return;
        }
        int n;
        try {
            n = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            msg(sender, "<red>Количество должно быть числом.");
            return;
        }
        if (n < 0) {
            msg(sender, "<red>Количество не может быть отрицательным.");
            return;
        }
        StatsManager.Stats s = plugin.stats().get(t.getUniqueId());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> s.titleTokens += n;
            case "take" -> s.titleTokens = Math.max(0, s.titleTokens - n);
            default -> {
                msg(sender, "<gray>/aegis token <give|take> <ник> <количество>");
                return;
            }
        }
        msg(sender, "<#E9D5FF>Токенов на титул у " + name(t) + ": <white>" + s.titleTokens);
        Player online = t.getPlayer();
        if (online != null && args[1].equalsIgnoreCase("give")) {
            online.sendMessage(plugin.prefixed("<#E9D5FF>Вы получили токен на титул (x" + n
                    + "). Инструкция: <white>/suffix"));
        }
    }

    // ---------------------------------------------------------------------- title

    private void title(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "<gray>/aegis title set <ник> <MiniMessage>  <dark_gray>(без токена и фильтра)");
            msg(sender, "<gray>/aegis title clear <ник>");
            return;
        }
        OfflinePlayer t = find(args[2]);
        if (t == null) {
            msg(sender, "<red>Игрок не найден.");
            return;
        }
        StatsManager.Stats s = plugin.stats().get(t.getUniqueId());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "clear" -> s.title = "";
            case "set" -> {
                if (args.length < 4) {
                    msg(sender, "<gray>/aegis title set <ник> <MiniMessage>");
                    return;
                }
                s.title = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            }
            default -> {
                msg(sender, "<gray>/aegis title <set|clear> <ник> ...");
                return;
            }
        }
        Player online = t.getPlayer();
        if (online != null) plugin.ranks().refresh(online);
        msg(sender, "<#E9D5FF>Титул " + name(t) + " обновлён.");
    }

    // ----------------------------------------------------------------------- case

    private void caseCmd(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            msg(sender, "<gray>/aegis case give <ник> <id кейса> [количество]  <dark_gray>(id из config.yml → cases)");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[2]);
        if (t == null) {
            msg(sender, "<red>Игрок не в сети.");
            return;
        }
        String id = args[3];
        if (!plugin.getConfig().isConfigurationSection("cases." + id)) {
            msg(sender, "<red>Такого кейса нет в config.yml (cases." + id + ").");
            return;
        }
        int n = 1;
        if (args.length >= 5) {
            try {
                n = Integer.parseInt(args[4]);
            } catch (NumberFormatException ignored) {
                // keep 1
            }
        }
        plugin.cases().give(t, id, n);
        msg(sender, "<#E9D5FF>Кейс <white>" + id + "</white> выдан игроку " + t.getName());
    }

    // ----------------------------------------------------------------------- bind

    private void bind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "<red>Только для игроков.");
            return;
        }
        if (args.length < 3) {
            msg(sender, "<gray>/aegis bind item <действие|clear>  <dark_gray>- предмет в руке");
            msg(sender, "<gray>/aegis bind npc <действие|clear>  <dark_gray>- затем ПКМ по NPC/существу (30 сек)");
            msg(sender, "<gray>Действия: <white>donate</white>, <white>cmd:команда</white>, <white>console:команда %player%");
            return;
        }
        String action = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        boolean clear = action.equalsIgnoreCase("clear");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "item" -> {
                if (!plugin.binds().bindItem(p, clear ? null : action)) {
                    msg(sender, "<red>Возьмите предмет в основную руку.");
                    return;
                }
                msg(sender, clear ? "<#E9D5FF>Привязка предмета снята."
                        : "<#E9D5FF>Предмет привязан: ПКМ выполнит <white>" + action);
            }
            case "npc", "entity" -> {
                plugin.binds().bindNextEntity(p, clear ? null : action);
                msg(sender, "<#E9D5FF>Нажмите ПКМ по NPC/существу в течение 30 секунд.");
            }
            default -> msg(sender, "<gray>/aegis bind <item|npc> <действие|clear>");
        }
    }

    private void npc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "<red>Только для игроков.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("spawn")) {
            msg(sender, "<gray>/aegis npc spawn [действие] [имя]  <dark_gray>- житель-NPC на вашем месте (по умолчанию donate)");
            return;
        }
        String action = args.length >= 3 ? args[2] : "donate";
        String name = args.length >= 4
                ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                : "<gradient:#7B3FF2:#E9D5FF><bold>Донат</bold></gradient>";
        plugin.binds().spawnNpc(p, action, name);
        msg(sender, "<#E9D5FF>NPC создан. Удалить: <white>/kill</white> по нему (или привяжите свой NPC через /aegis bind npc).");
    }

    // ----------------------------------------------------------------------- misc

    private OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private static String name(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : p.getUniqueId().toString();
    }

    private void help(CommandSender s) {
        msg(s, "<#E9D5FF>Команды AEGIS:");
        s.sendMessage(AegisCore.MM.deserialize(
                "<gray> /aegis reload <dark_gray>- перезагрузить конфиг и пересобрать пак\n"
                        + "<gray> /aegis hud <hub|kb> [ник|*] <dark_gray>- переключить худ\n"
                        + "<gray> /aegis game <start|stop|skip|status> <dark_gray>- королевская битва\n"
                        + "<gray> /aegis pack <status|rebuild|send|reset> <dark_gray>- ресурспак\n"
                        + "<gray> /aegis item <модель> [материал] [кол-во] <dark_gray>- предмет с моделью из пака\n"
                        + "<gray> /aegis rank give <ник> <vip|vipplus|none> [дней] <dark_gray>- выдать ранг\n"
                        + "<gray> /aegis token <give|take> <ник> <n> <dark_gray>- токены на титул\n"
                        + "<gray> /aegis title <set|clear> <ник> <текст> <dark_gray>- титул вручную\n"
                        + "<gray> /aegis case give <ник> <id> [n] <dark_gray>- выдать кейс\n"
                        + "<gray> /aegis bind <item|npc> <действие|clear> <dark_gray>- привязать /donate к предмету или NPC\n"
                        + "<gray> /aegis npc spawn [действие] <dark_gray>- создать NPC-жителя\n"
                        + "<gray> /amethyst <give|take|set|balance|item> <dark_gray>- валюта"));
    }

    private void msg(CommandSender s, String mm) {
        s.sendMessage(plugin.prefixed(mm));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("help", "reload", "hud", "game", "pack", "item", "rank", "token", "title", "case", "bind", "npc"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "hud" -> out.addAll(List.of("hub", "kb"));
                case "game" -> out.addAll(List.of("start", "stop", "skip", "status"));
                case "pack" -> out.addAll(List.of("status", "rebuild", "send", "reset"));
                case "item" -> out.addAll(List.of("amethyst", "legendary/"));
                case "rank" -> out.addAll(List.of("give", "info"));
                case "token" -> out.addAll(List.of("give", "take"));
                case "title" -> out.addAll(List.of("set", "clear"));
                case "case" -> out.add("give");
                case "bind" -> out.addAll(List.of("item", "npc"));
                case "npc" -> out.add("spawn");
                default -> {
                }
            }
        } else if (args.length == 3) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            if (a0.equals("hud") || (a0.equals("pack") && args[1].equalsIgnoreCase("send"))) {
                out.add("*");
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (List.of("rank", "token", "title", "case").contains(a0)) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (a0.equals("bind")) {
                out.addAll(List.of("donate", "clear", "cmd:", "console:"));
            } else if (a0.equals("npc")) {
                out.add("donate");
            } else if (a0.equals("item")) {
                for (Material m : List.of(Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_AXE,
                        Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.PAPER)) {
                    out.add(m.name());
                }
            }
        } else if (args.length == 4) {
            String a0 = args[0].toLowerCase(Locale.ROOT);
            if (a0.equals("rank") && args[1].equalsIgnoreCase("give")) out.addAll(List.of("vip", "vipplus", "none"));
            if (a0.equals("case")) {
                var sec = plugin.getConfig().getConfigurationSection("cases");
                if (sec != null) out.addAll(sec.getKeys(false));
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    @SuppressWarnings("unused")
    private static Component unused() {
        return Component.empty();
    }
}
