package org.example.lab04.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class UserSettings {
    private static final String SETTINGS_FILE = "user_settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String interfaceLanguage = "vi"; // "vi" or "en"
    private String defaultTargetLanguage = "vi";
    private boolean autoTranslate = true;
    private boolean translateOnlyForeign = true;
    private boolean showLocation = true;
    private String manualLocation = "";
    private Map<String, String> perChatTargetLanguage = new HashMap<>();

    public UserSettings() {}

    public static UserSettings load() {
        File file = new File(SETTINGS_FILE);
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                UserSettings settings = GSON.fromJson(reader, UserSettings.class);
                if (settings != null) {
                    if (settings.perChatTargetLanguage == null) {
                        settings.perChatTargetLanguage = new HashMap<>();
                    }
                    return settings;
                }
            } catch (Exception e) {
                System.err.println("Không thể đọc file cấu hình: " + e.getMessage());
            }
        }
        UserSettings defaultSettings = new UserSettings();
        defaultSettings.save();
        return defaultSettings;
    }

    public synchronized void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(SETTINGS_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            System.err.println("Lỗi lưu file cấu hình: " + e.getMessage());
        }
    }

    public void resetToDefaults() {
        this.interfaceLanguage = "vi";
        this.defaultTargetLanguage = "vi";
        this.autoTranslate = true;
        this.translateOnlyForeign = true;
        this.showLocation = true;
        this.manualLocation = "";
        this.perChatTargetLanguage.clear();
        save();
    }

    public String getTargetLanguageForUser(String targetUser) {
        if (targetUser != null && perChatTargetLanguage.containsKey(targetUser)) {
            return perChatTargetLanguage.get(targetUser);
        }
        return defaultTargetLanguage != null ? defaultTargetLanguage : "vi";
    }

    public void setTargetLanguageForUser(String targetUser, String langCode) {
        if (targetUser != null && langCode != null) {
            perChatTargetLanguage.put(targetUser, langCode);
            save();
        }
    }

    // Getters and Setters
    public String getInterfaceLanguage() {
        return interfaceLanguage != null ? interfaceLanguage : "vi";
    }

    public void setInterfaceLanguage(String interfaceLanguage) {
        this.interfaceLanguage = interfaceLanguage;
        save();
    }

    public String getDefaultTargetLanguage() {
        return defaultTargetLanguage != null ? defaultTargetLanguage : "vi";
    }

    public void setDefaultTargetLanguage(String defaultTargetLanguage) {
        this.defaultTargetLanguage = defaultTargetLanguage;
        save();
    }

    public boolean isAutoTranslate() {
        return autoTranslate;
    }

    public void setAutoTranslate(boolean autoTranslate) {
        this.autoTranslate = autoTranslate;
        save();
    }

    public boolean isTranslateOnlyForeign() {
        return translateOnlyForeign;
    }

    public void setTranslateOnlyForeign(boolean translateOnlyForeign) {
        this.translateOnlyForeign = translateOnlyForeign;
        save();
    }

    public boolean isShowLocation() {
        return showLocation;
    }

    public void setShowLocation(boolean showLocation) {
        this.showLocation = showLocation;
        save();
    }

    public String getManualLocation() {
        return manualLocation;
    }

    public void setManualLocation(String manualLocation) {
        this.manualLocation = manualLocation;
        save();
    }

    public Map<String, String> getPerChatTargetLanguage() {
        return perChatTargetLanguage;
    }
}
