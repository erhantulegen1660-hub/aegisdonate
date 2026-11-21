package ru.aegis.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Custom titles (suffix next to the nick). Players write MiniMessage; only colours, decorations,
 * gradients, rainbow and transition are allowed (no click/hover/font/etc.), length and swear words are checked.
 */
public final class TitleManager {

    public record Result(Component component, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private static final Map<Character, Character> LOOKALIKE = Map.ofEntries(
            Map.entry('a', 'а'), Map.entry('e', 'е'), Map.entry('o', 'о'), Map.entry('p', 'р'),
            Map.entry('c', 'с'), Map.entry('x', 'х'), Map.entry('y', 'у'), Map.entry('k', 'к'),
            Map.entry('m', 'м'), Map.entry('t', 'т'), Map.entry('h', 'н'), Map.entry('b', 'в'),
            Map.entry('0', 'о'), Map.entry('3', 'з'), Map.entry('6', 'б'), Map.entry('4', 'ч'),
            Map.entry('@', 'а'), Map.entry('$', 'с'), Map.entry('ё', 'е'));

    private final AegisCore plugin;
    private final MiniMessage restricted = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.transition(),
                    StandardTags.reset()))
            .build();

    private List<String> banned = List.of();

    public TitleManager(AegisCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        List<String> list = new ArrayList<>();
        for (String w : plugin.getConfig().getStringList("title.banned-words")) {
            String n = collapse(letters(w.toLowerCase(Locale.ROOT)));
            if (!n.isEmpty()) list.add(n);
        }
        banned = List.copyOf(list);
    }

    public int maxLength() {
        return Math.max(3, plugin.getConfig().getInt("title.max-length", 16));
    }

    public String editorUrl() {
        return plugin.getConfig().getString("title.editor-url", "https://webui.advntr.dev/");
    }

    /** Renders a stored title. Never throws. */
    public Component render(String raw) {
        if (raw == null || raw.isBlank()) return Component.empty();
        try {
            return restricted.deserialize(raw);
        } catch (Exception ex) {
            return Component.empty();
        }
    }

    /** Validates player input. */
    public Result check(String raw) {
        if (raw == null || raw.isBlank()) return new Result(null, "Титул пустой.");
        if (raw.length() > 300) return new Result(null, "Слишком длинная запись.");
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch < 32 || (ch >= '' && ch <= '')) {
                return new Result(null, "В титуле есть недопустимые символы.");
            }
        }
        Component comp;
        try {
            comp = restricted.deserialize(raw);
        } catch (Exception ex) {
            return new Result(null, "Не удалось разобрать формат. Проверьте теги в редакторе.");
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(comp).trim();
        int len = plain.codePointCount(0, plain.length());
        if (len == 0) return new Result(null, "В титуле нет видимого текста.");
        if (len > maxLength()) {
            return new Result(null, "Слишком длинный титул: " + len + " симв. (максимум " + maxLength() + ", теги не считаются).");
        }
        if (containsBanned(plain)) {
            return new Result(null, "Нецензурная лексика и оскорбления запрещены.");
        }
        return new Result(comp, null);
    }

    private boolean containsBanned(String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        String direct = collapse(letters(lower));
        StringBuilder mapped = new StringBuilder();
        for (char c : lower.toCharArray()) {
            mapped.append(LOOKALIKE.getOrDefault(c, c));
        }
        String viaMap = collapse(letters(mapped.toString()));
        for (String w : banned) {
            if (direct.contains(w) || viaMap.contains(w)) return true;
        }
        return false;
    }

    private static String letters(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= 'а' && c <= 'я') || c == 'ё' || (c >= 'a' && c <= 'z')) sb.append(c);
        }
        return sb.toString();
    }

    private static String collapse(String s) {
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (char c : s.toCharArray()) {
            if (c != prev) sb.append(c);
            prev = c;
        }
        return sb.toString();
    }

    /** Step-by-step instruction for players. */
    public void sendHelp(CommandSender to) {
        String url = editorUrl();
        to.sendMessage(plugin.prefixed("<#E9D5FF>Как сделать свой титул:"));
        to.sendMessage(AegisCore.MM.deserialize("<gray>1. Откройте редактор: ")
                .append(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));
        to.sendMessage(AegisCore.MM.deserialize(
                "<gray>2. В редакторе напишите текст и оформите его (цвет, градиент, жирный). "
                        + "Готовую запись с тегами, например <#C084FC><gradient:#7B3FF2:#E9D5FF>Legend</gradient></#C084FC>, скопируйте."));
        to.sendMessage(AegisCore.MM.deserialize(
                "<gray>3. В игре: <white>/suffix <ваша запись></white> (тратится 1 токен титула)."));
        to.sendMessage(AegisCore.MM.deserialize(
                "<gray>Проверить без траты токена: <white>/suffix preview <запись></white>. Убрать титул: <white>/suffix reset</white>."));
        to.sendMessage(AegisCore.MM.deserialize(
                "<gray>Максимум <white>" + maxLength() + "</white> видимых символов. "
                        + "<red>Нельзя:</red> нецензурная лексика, оскорбления, реклама. "
                        + "Администрация может снять нарушающий титул без возврата токена."));
    }
}
