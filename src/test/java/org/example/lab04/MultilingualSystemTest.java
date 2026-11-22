package org.example.lab04;

import org.example.lab04.model.LanguageItem;
import org.example.lab04.model.TranslationResult;
import org.example.lab04.model.UserLocation;
import org.example.lab04.model.UserSettings;
import org.example.lab04.service.GeoLocationService;
import org.example.lab04.service.I18nService;
import org.example.lab04.service.TranslationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MultilingualSystemTest {

    @Test
    public void testCountryCodeToEmoji() {
        Assertions.assertEquals("🇻🇳", UserLocation.countryCodeToEmoji("VN"));
        Assertions.assertEquals("🇺🇸", UserLocation.countryCodeToEmoji("US"));
        Assertions.assertEquals("🇯🇵", UserLocation.countryCodeToEmoji("JP"));
        Assertions.assertEquals("🇰🇷", UserLocation.countryCodeToEmoji("KR"));
        Assertions.assertEquals("🇫🇷", UserLocation.countryCodeToEmoji("FR"));
    }

    @Test
    public void testUserLocationDisplay() {
        UserLocation loc = new UserLocation("Vietnam", "VN", "Da Nang", "DN", "Asia/Ho_Chi_Minh");
        Assertions.assertEquals("🇻🇳 Da Nang, Vietnam", loc.getDisplayLocation());

        UserLocation hidden = UserLocation.hidden();
        Assertions.assertTrue(hidden.getDisplayLocation().contains("Ẩn"));

        UserLocation custom = UserLocation.fromCustom("Tokyo, Japan");
        Assertions.assertEquals("Tokyo, Japan", custom.getDisplayLocation());
    }

    @Test
    public void testLanguageItems() {
        LanguageItem vi = LanguageItem.findByCode("vi");
        Assertions.assertNotNull(vi);
        Assertions.assertEquals("🇻🇳", vi.getFlagEmoji());
        Assertions.assertTrue(vi.getName().contains("Tiếng Việt"));

        LanguageItem en = LanguageItem.findByCode("en");
        Assertions.assertNotNull(en);
        Assertions.assertEquals("🇬🇧", en.getFlagEmoji());

        Assertions.assertTrue(LanguageItem.SUPPORTED_LANGUAGES.size() >= 12);
    }

    @Test
    public void testUserSettingsPersistence() {
        UserSettings settings = UserSettings.load();
        Assertions.assertNotNull(settings);

        settings.setInterfaceLanguage("vi");
        settings.setDefaultTargetLanguage("en");
        settings.setAutoTranslate(true);
        settings.setTargetLanguageForUser("Alice", "ja");

        Assertions.assertEquals("ja", settings.getTargetLanguageForUser("Alice"));
        Assertions.assertEquals("en", settings.getTargetLanguageForUser("Bob")); // fallback to default

        settings.resetToDefaults();
        Assertions.assertEquals("vi", settings.getDefaultTargetLanguage());
    }

    @Test
    public void testI18nService() {
        I18nService.setLanguage("vi");
        Assertions.assertEquals("Gửi", I18nService.get("btn.send"));

        I18nService.setLanguage("en");
        Assertions.assertEquals("Send", I18nService.get("btn.send"));

        I18nService.setLanguage("vi"); // restore
    }

    @Test
    public void testTranslationServiceRealApi() throws Exception {
        CompletableFuture<TranslationResult> future = TranslationService.translateAsync(
                "Hello, how are you today?", "vi"
        );
        TranslationResult result = future.get(8, TimeUnit.SECONDS);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess(), "Translation should succeed: " + result.getErrorMessage());
        Assertions.assertNotNull(result.getTranslatedText());
        Assertions.assertFalse(result.getTranslatedText().trim().isEmpty());
        System.out.println("Translated text: " + result.getTranslatedText());
        System.out.println("Detected source lang: " + result.getSourceLang());

        Assertions.assertEquals("en", result.getSourceLang());
        Assertions.assertEquals("vi", result.getTargetLang());
    }

    @Test
    public void testGeoLocationService() throws Exception {
        UserSettings settings = new UserSettings();
        settings.setShowLocation(true);

        CompletableFuture<UserLocation> future = GeoLocationService.resolveCurrentLocation(settings);
        UserLocation loc = future.get(8, TimeUnit.SECONDS);

        Assertions.assertNotNull(loc);
        System.out.println("Detected location: " + loc.getDisplayLocation());
        Assertions.assertFalse(loc.getDisplayLocation().isEmpty());
    }

    @Test
    public void testUserRoles() {
        Assertions.assertEquals(org.example.lab04.model.UserRole.ADMIN, org.example.lab04.model.UserRole.fromString("ADMIN"));
        Assertions.assertEquals(org.example.lab04.model.UserRole.MODERATOR, org.example.lab04.model.UserRole.fromString("MOD"));
        Assertions.assertEquals(org.example.lab04.model.UserRole.USER, org.example.lab04.model.UserRole.fromString("USER"));
    }

    @Test
    public void testMongoDbServiceFallback() {
        org.example.lab04.service.MongoDbService.init();
        org.example.lab04.model.ChatMessageModel msg = new org.example.lab04.model.ChatMessageModel(
                "Alice", "Bob", org.example.lab04.model.ChatMessageModel.MessageType.TEXT, "Hello MongoDB"
        );
        org.example.lab04.service.MongoDbService.saveMessage(msg);

        java.util.List<org.example.lab04.model.ChatMessageModel> history =
                org.example.lab04.service.MongoDbService.getChatHistory("Alice", "Bob", 10);
        Assertions.assertFalse(history.isEmpty());
        Assertions.assertEquals("Hello MongoDB", history.get(history.size() - 1).getContent());
    }
}
