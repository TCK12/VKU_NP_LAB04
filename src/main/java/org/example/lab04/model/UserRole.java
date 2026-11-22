package org.example.lab04.model;

public enum UserRole {
    ADMIN("👑 ADMIN", "#F38BA8"),
    MODERATOR("🛡️ MOD", "#F9E2AF"),
    USER("👤 USER", "#89B4FA");

    private final String displayName;
    private final String colorHex;

    UserRole(String displayName, String colorHex) {
        this.displayName = displayName;
        this.colorHex = colorHex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public static UserRole fromString(String str) {
        if (str == null) return USER;
        String s = str.trim().toUpperCase();
        if (s.contains("ADMIN")) return ADMIN;
        if (s.contains("MOD")) return MODERATOR;
        return USER;
    }
}
