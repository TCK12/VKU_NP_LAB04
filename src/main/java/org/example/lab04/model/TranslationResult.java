package org.example.lab04.model;

public class TranslationResult {
    private final String originalText;
    private final String translatedText;
    private final String sourceLang;
    private final String targetLang;
    private final boolean success;
    private final String errorMessage;
    private final boolean sameLanguage;

    public TranslationResult(String originalText, String translatedText, String sourceLang, String targetLang, boolean success, String errorMessage, boolean sameLanguage) {
        this.originalText = originalText;
        this.translatedText = translatedText;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.success = success;
        this.errorMessage = errorMessage;
        this.sameLanguage = sameLanguage;
    }

    public static TranslationResult success(String originalText, String translatedText, String sourceLang, String targetLang, boolean sameLanguage) {
        return new TranslationResult(originalText, translatedText, sourceLang, targetLang, true, null, sameLanguage);
    }

    public static TranslationResult failure(String originalText, String targetLang, String errorMessage) {
        return new TranslationResult(originalText, null, null, targetLang, false, errorMessage, false);
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isSameLanguage() {
        return sameLanguage;
    }
}
