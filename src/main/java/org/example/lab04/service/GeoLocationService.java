package org.example.lab04.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.lab04.model.UserLocation;
import org.example.lab04.model.UserSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GeoLocationService {
    private static volatile UserLocation cachedLocation = null;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    /**
     * Lấy vị trí địa lý của người dùng hiện tại (bất đồng bộ).
     * Áp dụng quyền riêng tư từ UserSettings.
     */
    public static CompletableFuture<UserLocation> resolveCurrentLocation(UserSettings settings) {
        return CompletableFuture.supplyAsync(() -> {
            if (settings != null && !settings.isShowLocation()) {
                return UserLocation.hidden();
            }

            if (settings != null && settings.getManualLocation() != null && !settings.getManualLocation().trim().isEmpty()) {
                return UserLocation.fromCustom(settings.getManualLocation().trim());
            }

            if (cachedLocation != null) {
                return cachedLocation;
            }

            // Gọi API nhận diện vị trí qua IP công khai
            UserLocation location = fetchFromIpApi();
            if (location == null) {
                location = fetchFromIpWhoIs();
            }

            if (location == null) {
                location = UserLocation.unknown();
            }

            cachedLocation = location;
            return location;
        });
    }

    /**
     * Dùng cho Server xác định vị trí của IP kết nối đến.
     */
    public static UserLocation resolveLocationForIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty() ||
                ipAddress.equals("127.0.0.1") || ipAddress.equals("localhost") ||
                ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
            // IP mạng nội bộ/loopback -> dùng IP công khai của máy chủ
            return cachedLocation != null ? cachedLocation : UserLocation.fromCustom("LAN / Local");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ipAddress))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                if ("success".equalsIgnoreCase(obj.get("status").getAsString())) {
                    String country = obj.has("country") ? obj.get("country").getAsString() : "";
                    String countryCode = obj.has("countryCode") ? obj.get("countryCode").getAsString() : "";
                    String city = obj.has("city") ? obj.get("city").getAsString() : "";
                    String region = obj.has("regionName") ? obj.get("regionName").getAsString() : "";
                    String timezone = obj.has("timezone") ? obj.get("timezone").getAsString() : "";
                    return new UserLocation(country, countryCode, city, region, timezone);
                }
            }
        } catch (Exception ignored) {}

        return UserLocation.unknown();
    }

    private static UserLocation fetchFromIpApi() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                if (obj.has("status") && "success".equalsIgnoreCase(obj.get("status").getAsString())) {
                    String country = obj.has("country") ? obj.get("country").getAsString() : "";
                    String countryCode = obj.has("countryCode") ? obj.get("countryCode").getAsString() : "";
                    String city = obj.has("city") ? obj.get("city").getAsString() : "";
                    String region = obj.has("regionName") ? obj.get("regionName").getAsString() : "";
                    String timezone = obj.has("timezone") ? obj.get("timezone").getAsString() : "";
                    return new UserLocation(country, countryCode, city, region, timezone);
                }
            }
        } catch (Exception e) {
            System.err.println("ip-api error: " + e.getMessage());
        }
        return null;
    }

    private static UserLocation fetchFromIpWhoIs() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ipwho.is/"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                if (obj.has("success") && obj.get("success").getAsBoolean()) {
                    String country = obj.has("country") ? obj.get("country").getAsString() : "";
                    String countryCode = obj.has("country_code") ? obj.get("country_code").getAsString() : "";
                    String city = obj.has("city") ? obj.get("city").getAsString() : "";
                    String region = obj.has("region") ? obj.get("region").getAsString() : "";
                    String timezone = obj.has("timezone") && obj.get("timezone").isJsonObject()
                            ? obj.getAsJsonObject("timezone").get("id").getAsString() : "";
                    return new UserLocation(country, countryCode, city, region, timezone);
                }
            }
        } catch (Exception e) {
            System.err.println("ipwho.is error: " + e.getMessage());
        }
        return null;
    }

    public static void clearCache() {
        cachedLocation = null;
    }
}
