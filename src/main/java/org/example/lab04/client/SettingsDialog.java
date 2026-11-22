package org.example.lab04.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.lab04.model.LanguageItem;
import org.example.lab04.model.UserLocation;
import org.example.lab04.model.UserSettings;
import org.example.lab04.service.GeoLocationService;
import org.example.lab04.service.I18nService;
import org.example.lab04.service.TranslationService;

public class SettingsDialog {

    public interface SettingsListener {
        void onSettingsSaved(UserSettings settings);
    }

    public static void show(Stage owner, UserSettings settings, UserLocation currentLocation, SettingsListener listener) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle(I18nService.get("settings.title"));
        dialog.setResizable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1E1E2E; -fx-text-fill: #CDD6F4;");
        root.setPrefWidth(520);

        // Header Title
        Label lblHeader = new Label(I18nService.get("settings.title"));
        lblHeader.setFont(Font.font("SansSerif", FontWeight.BOLD, 17));
        lblHeader.setTextFill(Color.web("#CBA6F7"));

        // ── 1. Cài đặt Ngôn ngữ Giao diện ──────────────────────────────────────
        VBox secUi = new VBox(6);
        Label lblUiLang = new Label(I18nService.get("settings.interface_lang"));
        lblUiLang.setTextFill(Color.web("#CDD6F4"));
        lblUiLang.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));

        ComboBox<String> cbUiLang = new ComboBox<>();
        cbUiLang.getItems().addAll("🇻🇳 Tiếng Việt", "🇬🇧 English");
        cbUiLang.setValue("en".equalsIgnoreCase(settings.getInterfaceLanguage()) ? "🇬🇧 English" : "🇻🇳 Tiếng Việt");
        cbUiLang.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(cbUiLang);
        secUi.getChildren().addAll(lblUiLang, cbUiLang);

        // ── 2. Cài đặt Ngôn ngữ Dịch ──────────────────────────────────────────
        VBox secTrans = new VBox(8);
        Label lblTransLang = new Label(I18nService.get("settings.default_target_lang"));
        lblTransLang.setTextFill(Color.web("#CDD6F4"));
        lblTransLang.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));

        ComboBox<LanguageItem> cbTargetLang = new ComboBox<>();
        cbTargetLang.getItems().addAll(LanguageItem.SUPPORTED_LANGUAGES);
        cbTargetLang.setValue(LanguageItem.findByCode(settings.getDefaultTargetLanguage()));
        cbTargetLang.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(cbTargetLang);

        CheckBox chkAutoTranslate = new CheckBox(I18nService.get("settings.auto_translate"));
        chkAutoTranslate.setSelected(settings.isAutoTranslate());
        chkAutoTranslate.setTextFill(Color.web("#CDD6F4"));

        CheckBox chkOnlyForeign = new CheckBox(I18nService.get("settings.translate_only_foreign"));
        chkOnlyForeign.setSelected(settings.isTranslateOnlyForeign());
        chkOnlyForeign.setTextFill(Color.web("#CDD6F4"));

        secTrans.getChildren().addAll(lblTransLang, cbTargetLang, chkAutoTranslate, chkOnlyForeign);

        // ── 3. Cài đặt Vị trí & Quyền riêng tư ─────────────────────────────────
        VBox secLoc = new VBox(8);
        Label lblLocTitle = new Label("📍 " + (I18nService.getLanguage().equals("vi") ? "Vị Trí & Quyền Riêng Tư" : "Location & Privacy"));
        lblLocTitle.setTextFill(Color.web("#CDD6F4"));
        lblLocTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));

        CheckBox chkShowLoc = new CheckBox(I18nService.get("settings.show_location"));
        chkShowLoc.setSelected(settings.isShowLocation());
        chkShowLoc.setTextFill(Color.web("#CDD6F4"));

        HBox manualLocBox = new HBox(8);
        manualLocBox.setAlignment(Pos.CENTER_LEFT);
        Label lblManual = new Label(I18nService.get("settings.manual_location"));
        lblManual.setTextFill(Color.web("#A6ADC8"));

        TextField txtManualLoc = new TextField(settings.getManualLocation() != null ? settings.getManualLocation() : "");
        txtManualLoc.setPromptText("ví dụ: 🇻🇳 Da Nang, Vietnam");
        txtManualLoc.setStyle("-fx-background-color: #2A2A3E; -fx-text-fill: #CDD6F4; -fx-border-color: #585B70; -fx-border-radius: 6; -fx-background-radius: 6;");
        HBox.setHgrow(txtManualLoc, Priority.ALWAYS);
        manualLocBox.getChildren().addAll(lblManual, txtManualLoc);

        Label lblCurrentLoc = new Label((I18nService.getLanguage().equals("vi") ? "Vị trí phát hiện hiện tại: " : "Current detected location: ")
                + (currentLocation != null ? currentLocation.getDisplayLocation() : "Unknown"));
        lblCurrentLoc.setTextFill(Color.web("#89B4FA"));
        lblCurrentLoc.setFont(Font.font("SansSerif", 11));

        Button btnRefreshLoc = new Button(I18nService.getLanguage().equals("vi") ? "🔄 Tự động dò lại vị trí từ IP" : "🔄 Re-detect IP Location");
        btnRefreshLoc.setStyle(buttonStyle("#45475A", "#CDD6F4"));
        btnRefreshLoc.setOnAction(e -> {
            GeoLocationService.clearCache();
            lblCurrentLoc.setText("⏳ " + (I18nService.getLanguage().equals("vi") ? "Đang xác định lại vị trí..." : "Detecting location..."));
            GeoLocationService.resolveCurrentLocation(settings).thenAccept(loc -> {
                javafx.application.Platform.runLater(() -> {
                    lblCurrentLoc.setText((I18nService.getLanguage().equals("vi") ? "Vị trí phát hiện: " : "Detected location: ") + loc.getDisplayLocation());
                });
            });
        });

        secLoc.getChildren().addAll(lblLocTitle, chkShowLoc, manualLocBox, lblCurrentLoc, btnRefreshLoc);

        // ── 4. Action Buttons ──────────────────────────────────────────────────
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Button btnReset = new Button(I18nService.get("settings.reset_btn"));
        btnReset.setStyle(buttonStyle("#F38BA8", "#1E1E2E"));
        btnReset.setOnAction(e -> {
            settings.resetToDefaults();
            TranslationService.clearCache();
            cbUiLang.setValue("🇻🇳 Tiếng Việt");
            cbTargetLang.setValue(LanguageItem.findByCode("vi"));
            chkAutoTranslate.setSelected(true);
            chkOnlyForeign.setSelected(true);
            chkShowLoc.setSelected(true);
            txtManualLoc.clear();
            I18nService.setLanguage("vi");
            if (listener != null) listener.onSettingsSaved(settings);
            dialog.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button(I18nService.getLanguage().equals("vi") ? "Đóng" : "Close");
        btnClose.setStyle(buttonStyle("#585B70", "#CDD6F4"));
        btnClose.setOnAction(e -> dialog.close());

        Button btnSave = new Button(I18nService.get("settings.save_btn"));
        btnSave.setStyle(buttonStyle("#A6E3A1", "#1E1E2E"));
        btnSave.setOnAction(e -> {
            String selectedUi = cbUiLang.getValue().contains("English") ? "en" : "vi";
            settings.setInterfaceLanguage(selectedUi);
            I18nService.setLanguage(selectedUi);

            LanguageItem selectedTarget = cbTargetLang.getValue();
            if (selectedTarget != null) {
                settings.setDefaultTargetLanguage(selectedTarget.getCode());
            }

            settings.setAutoTranslate(chkAutoTranslate.isSelected());
            settings.setTranslateOnlyForeign(chkOnlyForeign.isSelected());
            settings.setShowLocation(chkShowLoc.isSelected());
            settings.setManualLocation(txtManualLoc.getText().trim());
            settings.save();

            if (listener != null) {
                listener.onSettingsSaved(settings);
            }
            dialog.close();
        });

        buttonBar.getChildren().addAll(btnReset, spacer, btnClose, btnSave);

        root.getChildren().addAll(
                lblHeader,
                new Separator(),
                secUi,
                new Separator(),
                secTrans,
                new Separator(),
                secLoc,
                new Separator(),
                buttonBar
        );

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    private static void styleComboBox(ComboBox<?> cb) {
        cb.setStyle("-fx-background-color: #2A2A3E; -fx-font-size: 13px; -fx-text-fill: #CDD6F4; "
                + "-fx-border-color: #585B70; -fx-border-radius: 6; -fx-background-radius: 6;");
    }

    private static String buttonStyle(String bgColor, String fgColor) {
        return "-fx-background-color: " + bgColor + "; "
                + "-fx-text-fill: " + fgColor + "; "
                + "-fx-font-weight: bold; "
                + "-fx-font-size: 12px; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 8 16 8 16; "
                + "-fx-cursor: hand;";
    }
}
