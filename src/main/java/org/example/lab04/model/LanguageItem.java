package org.example.lab04.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LanguageItem {
    private final String code;
    private final String name;
    private final String flagEmoji;

    public LanguageItem(String code, String name, String flagEmoji) {
        this.code = code;
        this.name = name;
        this.flagEmoji = flagEmoji;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getFlagEmoji() {
        return flagEmoji;
    }

    public String getDisplayName() {
        return flagEmoji + " " + name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LanguageItem that = (LanguageItem) o;
        return code.equalsIgnoreCase(that.code);
    }

    @Override
    public int hashCode() {
        return code.toLowerCase().hashCode();
    }

    public static final List<LanguageItem> SUPPORTED_LANGUAGES;

    static {
        List<LanguageItem> list = new ArrayList<>();
        list.add(new LanguageItem("auto", "Tự động nhận diện (Auto-detect)", "🌐"));
        list.add(new LanguageItem("vi", "Tiếng Việt (Vietnamese)", "🇻🇳"));
        list.add(new LanguageItem("en", "English", "🇬🇧"));
        list.add(new LanguageItem("zh-CN", "中文 (Chinese)", "🇨🇳"));
        list.add(new LanguageItem("ja", "日本語 (Japanese)", "🇯🇵"));
        list.add(new LanguageItem("ko", "한국어 (Korean)", "🇰🇷"));
        list.add(new LanguageItem("fr", "Français (French)", "🇫🇷"));
        list.add(new LanguageItem("de", "Deutsch (German)", "🇩🇪"));
        list.add(new LanguageItem("es", "Español (Spanish)", "🇪🇸"));
        list.add(new LanguageItem("pt", "Português (Portuguese)", "🇵🇹"));
        list.add(new LanguageItem("th", "ไทย (Thai)", "🇹🇭"));
        list.add(new LanguageItem("id", "Bahasa Indonesia", "🇮🇩"));
        list.add(new LanguageItem("ru", "Русский (Russian)", "🇷🇺"));
        list.add(new LanguageItem("it", "Italiano (Italian)", "🇮🇹"));
        list.add(new LanguageItem("ar", "العربية (Arabic)", "🇸🇦"));
        SUPPORTED_LANGUAGES = Collections.unmodifiableList(list);
    }

    public static LanguageItem findByCode(String code) {
        if (code == null) return SUPPORTED_LANGUAGES.get(1); // default vi
        for (LanguageItem item : SUPPORTED_LANGUAGES) {
            if (item.getCode().equalsIgnoreCase(code)) {
                return item;
            }
        }
        // Fallback
        return new LanguageItem(code, code.toUpperCase(), "🌐");
    }

    public static String getFlagForCode(String code) {
        LanguageItem item = findByCode(code);
        return item != null ? item.getFlagEmoji() : "🌐";
    }

    public static String getNameForCode(String code) {
        LanguageItem item = findByCode(code);
        return item != null ? item.getName() : code;
    }
}
