package org.example.lab04.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.example.lab04.model.ChatMessageModel;
import org.example.lab04.model.LanguageItem;
import org.example.lab04.model.TranslationResult;
import org.example.lab04.model.UserRole;
import org.example.lab04.model.UserSettings;
import org.example.lab04.service.AudioRecordService;
import org.example.lab04.service.I18nService;
import org.example.lab04.service.TranslationService;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class MessageBubble extends HBox {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final String sender;
    private final String originalText;
    private final boolean isMine;
    private final UserSettings settings;
    private final ChatMessageModel.MessageType messageType;
    private final File mediaFile;
    private final UserRole senderRole;

    private String currentTargetLang;
    private TranslationResult translationResult;
    private boolean isTranslationExpanded = true;
    private boolean isTranslating = false;

    // UI elements
    private VBox bubbleBox;
    private Label lblOriginal;
    private VBox transBox;
    private Label lblTransHeader;
    private Label lblTranslatedText;
    private Button btnToggleExpand;
    private Button btnManualTranslate;
    private Button btnRetry;
    private Label lblStatus;

    public MessageBubble(String sender, String text, boolean isMine, UserSettings settings, String initialTargetLang,
                         ChatMessageModel.MessageType messageType, File mediaFile, UserRole senderRole) {
        this.sender = sender;
        this.originalText = text;
        this.isMine = isMine;
        this.settings = settings;
        this.messageType = messageType != null ? messageType : ChatMessageModel.MessageType.TEXT;
        this.mediaFile = mediaFile;
        this.senderRole = senderRole != null ? senderRole : UserRole.USER;
        this.currentTargetLang = initialTargetLang != null ? initialTargetLang : settings.getDefaultTargetLanguage();

        setupUI();

        // Kích hoạt dịch tự động cho tin nhắn dạng TEXT nếu bật autoTranslate
        if (this.messageType == ChatMessageModel.MessageType.TEXT && settings.isAutoTranslate()) {
            executeTranslation(false);
        } else if (this.messageType == ChatMessageModel.MessageType.TEXT) {
            showManualTranslateButton();
        }
    }

    // Constructor tiện lợi cho tin nhắn TEXT thông thường
    public MessageBubble(String sender, String text, boolean isMine, UserSettings settings, String initialTargetLang) {
        this(sender, text, isMine, settings, initialTargetLang, ChatMessageModel.MessageType.TEXT, null, UserRole.USER);
    }

    private void setupUI() {
        this.setPadding(new Insets(3, 10, 3, 10));
        this.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        bubbleBox = new VBox(6);
        bubbleBox.setMaxWidth(520);
        bubbleBox.setPadding(new Insets(10, 14, 10, 14));

        if (isMine) {
            bubbleBox.setStyle("-fx-background-color: #1E3A3A; -fx-background-radius: 14 14 2 14; "
                    + "-fx-border-color: #315858; -fx-border-radius: 14 14 2 14; -fx-border-width: 1; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 2);");
        } else {
            bubbleBox.setStyle("-fx-background-color: #24273A; -fx-background-radius: 14 14 14 2; "
                    + "-fx-border-color: #363A4F; -fx-border-radius: 14 14 14 2; -fx-border-width: 1; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 2);");
        }

        // Header: Tên người gửi + Badge Phân quyền + Thời gian
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label lblSender = new Label(isMine ? (I18nService.getLanguage().equals("vi") ? "Tôi" : "Me") : sender);
        lblSender.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblSender.setTextFill(isMine ? Color.web("#A6E3A1") : Color.web("#89B4FA"));

        // Badge phân quyền (👑 ADMIN, 🛡️ MOD, 👤 USER)
        Label lblRoleBadge = new Label(senderRole.getDisplayName());
        lblRoleBadge.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        lblRoleBadge.setTextFill(Color.web(senderRole.getColorHex()));
        lblRoleBadge.setStyle("-fx-background-color: #181825; -fx-padding: 1 5 1 5; -fx-background-radius: 4;");

        String timeStr = LocalTime.now().format(TIME_FMT);
        Label lblTime = new Label(timeStr);
        lblTime.setFont(Font.font("SansSerif", 10));
        lblTime.setTextFill(Color.web("#6C7086"));

        header.getChildren().addAll(lblSender, lblRoleBadge, lblTime);
        bubbleBox.getChildren().add(header);

        // Nội dung theo từng loại tin nhắn (TEXT, IMAGE, VIDEO, VOICE)
        switch (messageType) {
            case IMAGE:
                setupImageContent();
                break;
            case VIDEO:
                setupVideoContent();
                break;
            case VOICE:
                setupVoiceContent();
                break;
            case TEXT:
            default:
                setupTextContent();
                break;
        }

        this.getChildren().add(bubbleBox);
    }

    private void setupTextContent() {
        lblOriginal = new Label(originalText);
        lblOriginal.setWrapText(true);
        lblOriginal.setMaxWidth(500);
        lblOriginal.setFont(Font.font("SansSerif", 14));
        lblOriginal.setTextFill(Color.web("#CDD6F4"));

        // Khối bản dịch bên dưới
        transBox = new VBox(4);
        transBox.setPadding(new Insets(8, 10, 8, 10));
        transBox.setStyle("-fx-background-color: #181825; -fx-background-radius: 8; -fx-border-color: #3A3A5C; -fx-border-width: 0 0 0 3;");

        HBox transHeaderBar = new HBox(6);
        transHeaderBar.setAlignment(Pos.CENTER_LEFT);

        lblTransHeader = new Label();
        lblTransHeader.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        lblTransHeader.setTextFill(Color.web("#FAB387"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnToggleExpand = new Button();
        btnToggleExpand.setStyle("-fx-background-color: transparent; -fx-text-fill: #A6ADC8; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 0 4 0 4;");
        btnToggleExpand.setOnAction(e -> toggleExpand());

        btnRetry = new Button("🔄");
        btnRetry.setStyle("-fx-background-color: transparent; -fx-text-fill: #F38BA8; -fx-font-size: 11px; -fx-cursor: hand;");
        btnRetry.setVisible(false);
        btnRetry.setOnAction(e -> executeTranslation(true));

        transHeaderBar.getChildren().addAll(lblTransHeader, spacer, btnRetry, btnToggleExpand);

        lblStatus = new Label();
        lblStatus.setFont(Font.font("SansSerif", 12));
        lblStatus.setTextFill(Color.web("#89DCEB"));

        lblTranslatedText = new Label();
        lblTranslatedText.setWrapText(true);
        lblTranslatedText.setMaxWidth(480);
        lblTranslatedText.setFont(Font.font("SansSerif", 13));
        lblTranslatedText.setTextFill(Color.web("#BAC2DE"));

        transBox.getChildren().addAll(transHeaderBar, lblStatus, lblTranslatedText);
        transBox.setVisible(false);
        transBox.setManaged(false);

        btnManualTranslate = new Button("🌐 " + I18nService.get("translation.manual_translate"));
        btnManualTranslate.setStyle("-fx-background-color: #313244; -fx-text-fill: #89B4FA; -fx-font-size: 11px; -fx-background-radius: 6; -fx-cursor: hand;");
        btnManualTranslate.setOnAction(e -> executeTranslation(true));
        btnManualTranslate.setVisible(false);
        btnManualTranslate.setManaged(false);

        bubbleBox.getChildren().addAll(lblOriginal, transBox, btnManualTranslate);
    }

    private void setupImageContent() {
        VBox imgBox = new VBox(6);
        imgBox.setPadding(new Insets(4, 0, 4, 0));

        Image img = null;
        if (mediaFile != null && mediaFile.exists()) {
            img = new Image(mediaFile.toURI().toString(), 340, 260, true, true);
        }

        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitWidth(320);
            iv.setFitHeight(240);

            // Bo góc hình ảnh
            Rectangle clip = new Rectangle(320, 240);
            clip.setArcWidth(12);
            clip.setArcHeight(12);
            iv.setClip(clip);
            iv.setStyle("-fx-cursor: hand;");

            final Image fullImage = img;
            iv.setOnMouseClicked(e -> {
                Stage stage = (Stage) this.getScene().getWindow();
                MediaViewerDialog.showImageViewer(stage, fullImage, mediaFile.getName());
            });

            Label lblHint = new Label("🖼️ " + (mediaFile != null ? mediaFile.getName() : "Hình ảnh") + " (Click để phóng to)");
            lblHint.setFont(Font.font("SansSerif", 11));
            lblHint.setTextFill(Color.web("#A6ADC8"));

            imgBox.getChildren().addAll(iv, lblHint);
        } else {
            Label lblFail = new Label("🖼️ Không thể tải hình ảnh: " + (mediaFile != null ? mediaFile.getName() : ""));
            lblFail.setTextFill(Color.web("#F38BA8"));
            imgBox.getChildren().add(lblFail);
        }

        bubbleBox.getChildren().add(imgBox);
    }

    private void setupVideoContent() {
        VBox vidBox = new VBox(8);
        vidBox.setPadding(new Insets(8));
        vidBox.setStyle("-fx-background-color: #181825; -fx-background-radius: 10; -fx-border-color: #313244; -fx-border-width: 1;");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label lblIcon = new Label("🎬");
        lblIcon.setFont(Font.font("SansSerif", 22));

        VBox info = new VBox(2);
        Label lblName = new Label(mediaFile != null ? mediaFile.getName() : "Video");
        lblName.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblName.setTextFill(Color.web("#CDD6F4"));

        Label lblSize = new Label(mediaFile != null ? formatSize(mediaFile.length()) : "");
        lblSize.setFont(Font.font("SansSerif", 10));
        lblSize.setTextFill(Color.web("#89DCEB"));
        info.getChildren().addAll(lblName, lblSize);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnPlay = new Button("▶ Xem Video");
        btnPlay.setStyle("-fx-background-color: #A6E3A1; -fx-text-fill: #1E1E2E; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
        btnPlay.setOnAction(e -> {
            Stage stage = (Stage) this.getScene().getWindow();
            MediaViewerDialog.showVideoPlayer(stage, mediaFile);
        });

        titleRow.getChildren().addAll(lblIcon, info, spacer, btnPlay);
        vidBox.getChildren().add(titleRow);
        bubbleBox.getChildren().add(vidBox);
    }

    private void setupVoiceContent() {
        HBox voiceRow = new HBox(10);
        voiceRow.setAlignment(Pos.CENTER_LEFT);
        voiceRow.setPadding(new Insets(6, 10, 6, 10));
        voiceRow.setStyle("-fx-background-color: #181825; -fx-background-radius: 8;");

        Button btnPlayAudio = new Button("▶ Phát");
        btnPlayAudio.setStyle("-fx-background-color: #89B4FA; -fx-text-fill: #1E1E2E; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;");

        Label lblVoiceTitle = new Label("🎙️ Tin nhắn thoại " + (originalText.isEmpty() ? "" : "(" + originalText + ")"));
        lblVoiceTitle.setFont(Font.font("SansSerif", 12));
        lblVoiceTitle.setTextFill(Color.web("#CDD6F4"));

        btnPlayAudio.setOnAction(e -> {
            if (btnPlayAudio.getText().contains("Phát") || btnPlayAudio.getText().contains("Play")) {
                btnPlayAudio.setText("⏹ Dừng");
                AudioRecordService.playAudio(mediaFile, () -> {
                    Platform.runLater(() -> btnPlayAudio.setText("▶ Phát"));
                });
            } else {
                AudioRecordService.stopAudio();
                btnPlayAudio.setText("▶ Phát");
            }
        });

        voiceRow.getChildren().addAll(btnPlayAudio, lblVoiceTitle);
        bubbleBox.getChildren().add(voiceRow);
    }

    public void retranslate(String newTargetLang) {
        if (messageType != ChatMessageModel.MessageType.TEXT) return;
        this.currentTargetLang = newTargetLang;
        executeTranslation(true);
    }

    public void executeTranslation(boolean force) {
        if (isTranslating || messageType != ChatMessageModel.MessageType.TEXT) return;

        Platform.runLater(() -> {
            isTranslating = true;
            if (btnManualTranslate != null) {
                btnManualTranslate.setVisible(false);
                btnManualTranslate.setManaged(false);
            }
            if (btnRetry != null) btnRetry.setVisible(false);

            transBox.setVisible(true);
            transBox.setManaged(true);
            lblTransHeader.setText(LanguageItem.getFlagForCode(currentTargetLang) + " " + I18nService.get("translation.label") + ":");
            lblStatus.setText(I18nService.get("translation.translating"));
            lblStatus.setVisible(true);
            lblStatus.setManaged(true);
            lblTranslatedText.setVisible(false);
            lblTranslatedText.setManaged(false);
            btnToggleExpand.setText(I18nService.get("translation.hide"));
        });

        TranslationService.translateAsync(originalText, currentTargetLang).thenAccept(res -> {
            Platform.runLater(() -> {
                isTranslating = false;
                this.translationResult = res;

                if (!res.isSuccess()) {
                    lblStatus.setText("❌ " + I18nService.get("translation.failed") + " " + res.getErrorMessage());
                    if (btnRetry != null) btnRetry.setVisible(true);
                    return;
                }

                if (res.isSameLanguage() && settings.isTranslateOnlyForeign() && !force) {
                    transBox.setVisible(false);
                    transBox.setManaged(false);
                    return;
                }

                lblStatus.setVisible(false);
                lblStatus.setManaged(false);

                String flag = LanguageItem.getFlagForCode(currentTargetLang);
                String srcName = res.getSourceLang() != null ? LanguageItem.getNameForCode(res.getSourceLang()) : "";
                String headerText = flag + " " + I18nService.get("translation.label");
                if (!srcName.isEmpty() && !srcName.equalsIgnoreCase(currentTargetLang)) {
                    headerText += " (" + I18nService.get("translation.from") + " " + srcName + "):";
                } else {
                    headerText += ":";
                }
                lblTransHeader.setText(headerText);

                lblTranslatedText.setText(res.getTranslatedText());
                lblTranslatedText.setVisible(true);
                lblTranslatedText.setManaged(true);
                btnToggleExpand.setText(I18nService.get("translation.hide"));
                isTranslationExpanded = true;
            });
        });
    }

    private void toggleExpand() {
        isTranslationExpanded = !isTranslationExpanded;
        if (isTranslationExpanded) {
            lblTranslatedText.setVisible(true);
            lblTranslatedText.setManaged(true);
            btnToggleExpand.setText(I18nService.get("translation.hide"));
        } else {
            lblTranslatedText.setVisible(false);
            lblTranslatedText.setManaged(false);
            btnToggleExpand.setText(I18nService.get("translation.show"));
        }
    }

    private void showManualTranslateButton() {
        if (btnManualTranslate != null) {
            btnManualTranslate.setVisible(true);
            btnManualTranslate.setManaged(true);
        }
        if (transBox != null) {
            transBox.setVisible(false);
            transBox.setManaged(false);
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024));
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getCurrentTargetLang() {
        return currentTargetLang;
    }

    public ChatMessageModel.MessageType getMessageType() {
        return messageType;
    }
}
