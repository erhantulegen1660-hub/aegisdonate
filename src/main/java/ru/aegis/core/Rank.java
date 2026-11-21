package ru.aegis.core;

/** Donate ranks. The glyph is a badge from the "aegis:hud" font of the resource pack. */
public enum Rank {
    NONE("none", "Игрок", '\0'),
    VIP("vip", "VIP", ''),
    VIPPLUS("vipplus", "VIP+", '');

    private final String key;
    private final String label;
    private final char glyph;

    Rank(String key, String label, char glyph) {
        this.key = key;
        this.label = label;
        this.glyph = glyph;
    }

    public String key() { return key; }

    public String label() { return label; }

    public char glyph() { return glyph; }

    public static Rank fromKey(String s) {
        if (s == null) return NONE;
        String k = s.trim().toLowerCase().replace("+", "plus");
        for (Rank r : values()) {
            if (r.key.equals(k)) return r;
        }
        return NONE;
    }
}
