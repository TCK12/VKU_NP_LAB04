package org.example.lab04.model;

import java.io.Serializable;

public class UserLocation implements Serializable {
    private String country = "";
    private String countryCode = "";
    private String city = "";
    private String region = "";
    private String timezone = "";
    private String flagEmoji = "🌐";
    private boolean isHidden = false;
    private String customDisplay = null;

    public UserLocation() {}

    public UserLocation(String country, String countryCode, String city, String region, String timezone) {
        this.country = country != null ? country : "";
        this.countryCode = countryCode != null ? countryCode.toUpperCase() : "";
        this.city = city != null ? city : "";
        this.region = region != null ? region : "";
        this.timezone = timezone != null ? timezone : "";
        this.flagEmoji = countryCodeToEmoji(this.countryCode);
    }

    public static UserLocation unknown() {
        UserLocation loc = new UserLocation();
        loc.country = "Unknown";
        loc.city = "Unknown";
        loc.flagEmoji = "📍";
        return loc;
    }

    public static UserLocation hidden() {
        UserLocation loc = new UserLocation();
        loc.isHidden = true;
        loc.flagEmoji = "🔒";
        loc.customDisplay = "Vị trí đã ẩn";
        return loc;
    }

    public static UserLocation fromCustom(String text) {
        UserLocation loc = new UserLocation();
        loc.customDisplay = text;
        loc.flagEmoji = "📍";
        return loc;
    }

    public static String countryCodeToEmoji(String code) {
        if (code == null || code.length() != 2) {
            return "🌐";
        }
        code = code.toUpperCase();
        int firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6;
        int secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6;
        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }

    public String getDisplayLocation() {
        if (isHidden) {
            return "🔒 Ẩn vị trí";
        }
        if (customDisplay != null && !customDisplay.trim().isEmpty()) {
            return customDisplay.trim();
        }
        if (country.isEmpty() && city.isEmpty()) {
            return "📍 Unknown location";
        }
        StringBuilder sb = new StringBuilder();
        if (!flagEmoji.isEmpty()) {
            sb.append(flagEmoji).append(" ");
        }
        if (!city.isEmpty()) {
            sb.append(city);
            if (!country.isEmpty()) {
                sb.append(", ").append(country);
            }
        } else if (!country.isEmpty()) {
            sb.append(country);
        }
        return sb.toString().trim();
    }

    // Getters and Setters
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
        this.flagEmoji = countryCodeToEmoji(countryCode);
    }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getFlagEmoji() { return flagEmoji; }
    public void setFlagEmoji(String flagEmoji) { this.flagEmoji = flagEmoji; }

    public boolean isHidden() { return isHidden; }
    public void setHidden(boolean hidden) { isHidden = hidden; }

    public String getCustomDisplay() { return customDisplay; }
    public void setCustomDisplay(String customDisplay) { this.customDisplay = customDisplay; }

    @Override
    public String toString() {
        return getDisplayLocation();
    }
}
