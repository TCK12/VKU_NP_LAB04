package org.example.lab04.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.lab04.model.TranslationResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranslationService {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Translation-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, TranslationResult> CACHE = new ConcurrentHashMap<>();

    /**
     * Dịch bất đồng bộ nội dung văn bản (không làm gián đoạn luồng UI hoặc luồng nhận tin nhắn).
     */
    public static CompletableFuture<TranslationResult> translateAsync(String text, String targetLang) {
        return translateAsync(text, "auto", targetLang);
    }

    public static CompletableFuture<TranslationResult> translateAsync(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    TranslationResult.success("", "", "auto", targetLang, true)
            );
        }

        if (targetLang == null || targetLang.equalsIgnoreCase("auto")) {
            targetLang = "vi";
        }

        final String cacheKey = targetLang.toLowerCase() + "::" + text.trim();
        TranslationResult cached = CACHE.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        final String finalTargetLang = targetLang;
        final String finalSourceLang = (sourceLang == null || sourceLang.isEmpty()) ? "auto" : sourceLang;

        return CompletableFuture.supplyAsync(() -> {
            // 1. Thử endpoint Google Clients5 (rất ổn định, không bị chặn 429)
            TranslationResult result = fetchGoogleClients5(text, finalSourceLang, finalTargetLang);

            // 2. Dự phòng: Google GTX
            if (result == null || !result.isSuccess()) {
                result = fetchGoogleGtx(text, finalSourceLang, finalTargetLang);
            }

            // 3. Dự phòng: MyMemory
            if (result == null || !result.isSuccess()) {
                result = fetchMyMemoryTranslate(text, finalSourceLang, finalTargetLang);
            }

            if (result != null && result.isSuccess()) {
                CACHE.put(cacheKey, result);
            }
            return result != null ? result : TranslationResult.failure(text, finalTargetLang, "Không thể kết nối đến máy chủ dịch thuật.");
        }, EXECUTOR);
    }

    private static TranslationResult fetchGoogleClients5(String text, String sourceLang, String targetLang) {
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
            String url = String.format(
                    "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=%s&tl=%s&q=%s",
                    sourceLang, targetLang, encodedText
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                JsonElement root = JsonParser.parseString(response.body());
                if (root.isJsonArray()) {
                    JsonArray rootArray = root.getAsJsonArray();
                    StringBuilder translatedBuilder = new StringBuilder();
                    String detectedSrc = sourceLang;

                    for (JsonElement elem : rootArray) {
                        if (elem.isJsonArray()) {
                            JsonArray pair = elem.getAsJsonArray();
                            if (pair.size() > 0 && !pair.get(0).isJsonNull()) {
                                if (translatedBuilder.length() > 0) translatedBuilder.append(" ");
                                translatedBuilder.append(pair.get(0).getAsString());
                            }
                            if (pair.size() > 1 && !pair.get(1).isJsonNull()) {
                                detectedSrc = pair.get(1).getAsString();
                            }
                        } else if (elem.isJsonPrimitive()) {
                            // Trường hợp trả về mảng 1 chiều ["translated", "src"]
                            if (translatedBuilder.length() == 0) {
                                translatedBuilder.append(elem.getAsString());
                            } else if (detectedSrc.equals(sourceLang)) {
                                detectedSrc = elem.getAsString();
                            }
                        }
                    }

                    String translated = translatedBuilder.toString();
                    boolean sameLang = detectedSrc != null && (
                            detectedSrc.equalsIgnoreCase(targetLang) ||
                            (detectedSrc.startsWith("zh") && targetLang.startsWith("zh")) ||
                            text.trim().equalsIgnoreCase(translated.trim())
                    );

                    return TranslationResult.success(text, translated, detectedSrc, targetLang, sameLang);
                }
            }
        } catch (Exception e) {
            System.err.println("Google Clients5 error: " + e.getMessage());
        }
        return null;
    }

    private static TranslationResult fetchGoogleGtx(String text, String sourceLang, String targetLang) {
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
            String url = String.format(
                    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s",
                    sourceLang, targetLang, encodedText
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                JsonElement root = JsonParser.parseString(response.body());
                if (root.isJsonArray()) {
                    JsonArray rootArray = root.getAsJsonArray();
                    StringBuilder translatedBuilder = new StringBuilder();
                    if (rootArray.size() > 0 && rootArray.get(0).isJsonArray()) {
                        JsonArray sentences = rootArray.get(0).getAsJsonArray();
                        for (JsonElement sentenceElem : sentences) {
                            if (sentenceElem.isJsonArray()) {
                                JsonArray part = sentenceElem.getAsJsonArray();
                                if (part.size() > 0 && !part.get(0).isJsonNull()) {
                                    translatedBuilder.append(part.get(0).getAsString());
                                }
                            }
                        }
                    }

                    String detectedSrc = sourceLang;
                    if (rootArray.size() > 2 && !rootArray.get(2).isJsonNull()) {
                        detectedSrc = rootArray.get(2).getAsString();
                    }

                    String translated = translatedBuilder.toString();
                    boolean sameLang = detectedSrc != null && (
                            detectedSrc.equalsIgnoreCase(targetLang) ||
                            text.trim().equalsIgnoreCase(translated.trim())
                    );

                    return TranslationResult.success(text, translated, detectedSrc, targetLang, sameLang);
                }
            }
        } catch (Exception e) {
            System.err.println("Google GTX error: " + e.getMessage());
        }
        return null;
    }

    private static TranslationResult fetchMyMemoryTranslate(String text, String sourceLang, String targetLang) {
        try {
            // MyMemory không chấp nhận "auto", nếu auto tạm mặc định en
            String src = sourceLang.equalsIgnoreCase("auto") ? "en" : sourceLang;
            String pair = src + "|" + targetLang;
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
            String encodedPair = URLEncoder.encode(pair, StandardCharsets.UTF_8);
            String url = String.format("https://api.mymemory.translated.net/get?q=%s&langpair=%s", encodedText, encodedPair);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("responseData") && json.get("responseData").isJsonObject()) {
                    JsonObject respData = json.getAsJsonObject("responseData");
                    String translated = respData.get("translatedText").getAsString();
                    boolean same = text.trim().equalsIgnoreCase(translated.trim());
                    return TranslationResult.success(text, translated, src, targetLang, same);
                }
            }
        } catch (Exception e) {
            System.err.println("MyMemory fallback error: " + e.getMessage());
        }
        return TranslationResult.failure(text, targetLang, "Lỗi kết nối dịch thuật.");
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
