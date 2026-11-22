package org.example.lab04.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.lab04.model.ChatMessageModel;
import org.example.lab04.model.LanguageItem;
import org.example.lab04.model.UserLocation;
import org.example.lab04.model.UserRole;
import org.example.lab04.model.UserSettings;
import org.example.lab04.network.INetworkCallback;
import org.example.lab04.network.P2PClient;
import org.example.lab04.network.P2PListener;
import org.example.lab04.service.AudioRecordService;
import org.example.lab04.service.GeoLocationService;
import org.example.lab04.service.I18nService;
import org.example.lab04.service.MongoDbService;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatClientApp extends Application {

    public static final Map<String, String> offeredFiles = new ConcurrentHashMap<>();

    private UserSettings settings;
    private UserLocation myLocation = UserLocation.unknown();
    private UserRole myRole = UserRole.USER;

    // Danh sách người dùng
    private final ObservableList<String> rawOnlineUsers = FXCollections.observableArrayList();
    private FilteredList<String> filteredUsers;
    private final List<MessageBubble> activeMessageBubbles = new ArrayList<>();

    // Các thành phần giao diện
    private Stage primaryStage;
    private ListView<String> userListView = new ListView<>();
    private TextField txtSearchUser = new TextField();
    private VBox chatBox = new VBox(12);
    private ScrollPane scrollPane = new ScrollPane();
    private TextField txtMessage = new TextField();

    // Nút chức năng
    private Button btnSend;
    private Button btnEmoji;
    private Button btnVoice;
    private Button btnSendImage;
    private Button btnSendVideo;
    private Button btnSendFile;
    private Button btnSettings;
    private Button btnQuickAutoTranslate;
    private ComboBox<LanguageItem> cbChatTargetLang;

    private EmojiPickerPopup emojiPicker;

    // Header Chat
    private Label lblChatHeaderUser = new Label();
    private Label lblChatHeaderLocation = new Label();
    private Label lblChatHeaderRole = new Label();
    private Label lblStatus = new Label();
    private Label lblMyInfo = new Label();
    private Label lblSidebarTitle = new Label();

    // Thông tin tài khoản & kết nối
    private String username;
    private int myP2PPort;
    private String serverIP = "127.0.0.1";
    private int serverPort = 9999;

    private String targetIP = null;
    private String targetUsername = null;
    private int targetP2PPort = 0;
    private String targetLocationDisplay = "";
    private UserRole targetRole = UserRole.USER;

    private Socket centralServerSocket;
    private DataOutputStream centralServerDos;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.settings = UserSettings.load();
        I18nService.setLanguage(settings.getInterfaceLanguage());

        // Khởi động kết nối MongoDB ngầm
        MongoDbService.init();

        if (!showLoginDialog()) {
            System.exit(0);
            return;
        }

        // Lấy quyền từ CSDL
        this.myRole = MongoDbService.getUserRole(username);

        // Bắt đầu nhận diện vị trí ngầm từ IP
        GeoLocationService.resolveCurrentLocation(settings).thenAccept(loc -> {
            this.myLocation = loc;
            Platform.runLater(this::updateMyInfoBadge);
        });

        setupUI();
        startP2PListener();
        connectAndListenCentralServer();
    }

    private void updateMyInfoBadge() {
        String locStr = myLocation != null ? myLocation.getDisplayLocation() : "Unknown location";
        lblMyInfo.setText(myRole.getDisplayName() + " " + username + "  [" + locStr + "]");
    }

    private boolean showLoginDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18nService.get("login.title"));

        TextField txtName = new TextField("User" + (int) (Math.random() * 100));
        TextField txtP2PPort = new TextField("8001");
        TextField txtServerIP = new TextField("127.0.0.1");
        TextField txtServerPort = new TextField("9999");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 20));

        grid.add(new Label(I18nService.get("login.name")), 0, 0);
        grid.add(txtName, 1, 0);
        grid.add(new Label(I18nService.get("login.p2p_port")), 0, 1);
        grid.add(txtP2PPort, 1, 1);
        grid.add(new Label(I18nService.get("login.server_ip")), 0, 2);
        grid.add(txtServerIP, 1, 2);
        grid.add(new Label(I18nService.get("login.server_port")), 0, 3);
        grid.add(txtServerPort, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            this.username = txtName.getText().trim();
            this.myP2PPort = Integer.parseInt(txtP2PPort.getText().trim());
            this.serverIP = txtServerIP.getText().trim();
            this.serverPort = Integer.parseInt(txtServerPort.getText().trim());
            return true;
        }
        return false;
    }

    private void connectAndListenCentralServer() {
        new Thread(() -> {
            try {
                centralServerSocket = new Socket(serverIP, serverPort);
                centralServerDos = new DataOutputStream(centralServerSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(centralServerSocket.getInputStream());

                // Đăng ký với giao thức REGISTER_V2 gửi kèm vị trí
                String loc = myLocation != null ? myLocation.getDisplayLocation() : "Unknown location";
                centralServerDos.writeUTF("REGISTER_V2");
                centralServerDos.writeUTF(username);
                centralServerDos.writeInt(myP2PPort);
                centralServerDos.writeUTF(loc);
                centralServerDos.flush();

                while (true) {
                    String header = dis.readUTF();
                    if ("UPDATE_USER_LIST".equals(header)) {
                        int count = dis.readInt();
                        List<String> onlineUsers = new ArrayList<>();

                        for (int i = 0; i < count; i++) {
                            String userStr = dis.readUTF();
                            if (!userStr.startsWith(username + " -")) {
                                onlineUsers.add(userStr);
                            } else {
                                // Cập nhật lại quyền của chính mình từ server
                                String[] myParts = userStr.split(" - ");
                                if (myParts.length > 3) {
                                    myRole = UserRole.fromString(myParts[3]);
                                    Platform.runLater(this::updateMyInfoBadge);
                                }
                            }
                        }

                        Platform.runLater(() -> {
                            rawOnlineUsers.setAll(onlineUsers);
                            if (onlineUsers.isEmpty()) {
                                lblStatus.setText(I18nService.get("status.no_other_users"));
                            } else {
                                lblStatus.setText(I18nService.get("status.list_updated"));
                            }
                        });
                    }
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    lblStatus.setText(I18nService.get("status.disconnected"));
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(I18nService.getLanguage().equals("vi") ? "Lỗi kết nối" : "Connection Error");
                    alert.setHeaderText(I18nService.getLanguage().equals("vi") ? "Server đã đóng hoặc mất kết nối." : "Server closed or connection lost.");
                    alert.setContentText(I18nService.getLanguage().equals("vi") ? "Ứng dụng sẽ tự động thoát." : "Application will exit.");
                    alert.showAndWait();
                    System.exit(0);
                });
            }
        }).start();
    }

    private void startP2PListener() {
        INetworkCallback uiCallback = new INetworkCallback() {
            @Override
            public void onMessageReceived(String sender, String message) {
                Platform.runLater(() -> {
                    String curTargetLang = getActiveConversationTargetLang();
                    UserRole senderRole = MongoDbService.getUserRole(sender);
                    MessageBubble bubble = new MessageBubble(sender, message, false, settings, curTargetLang,
                            ChatMessageModel.MessageType.TEXT, null, senderRole);
                    activeMessageBubbles.add(bubble);
                    chatBox.getChildren().add(bubble);
                    scrollPane.setVvalue(1.0);
                });
            }

            @Override
            public void onImageReceived(String sender, String filePath, byte[] imageBytes) {
                Platform.runLater(() -> {
                    String curTargetLang = getActiveConversationTargetLang();
                    UserRole senderRole = MongoDbService.getUserRole(sender);
                    File imgFile = new File(filePath);
                    MessageBubble bubble = new MessageBubble(sender, imgFile.getName(), false, settings, curTargetLang,
                            ChatMessageModel.MessageType.IMAGE, imgFile, senderRole);
                    activeMessageBubbles.add(bubble);
                    chatBox.getChildren().add(bubble);
                    scrollPane.setVvalue(1.0);
                });
            }

            @Override
            public void onVoiceReceived(String sender, int durationSec, byte[] voiceBytes) {
                Platform.runLater(() -> {
                    String curTargetLang = getActiveConversationTargetLang();
                    UserRole senderRole = MongoDbService.getUserRole(sender);
                    File dir = new File("downloads" + File.separator + "voice");
                    File voiceFile = new File(dir, "voice_latest.wav");
                    MessageBubble bubble = new MessageBubble(sender, durationSec + "s", false, settings, curTargetLang,
                            ChatMessageModel.MessageType.VOICE, voiceFile, senderRole);
                    activeMessageBubbles.add(bubble);
                    chatBox.getChildren().add(bubble);
                    scrollPane.setVvalue(1.0);
                });
            }

            @Override
            public void onVideoReceived(String sender, String filePath, byte[] videoBytes) {
                Platform.runLater(() -> {
                    String curTargetLang = getActiveConversationTargetLang();
                    UserRole senderRole = MongoDbService.getUserRole(sender);
                    File vidFile = new File(filePath);
                    MessageBubble bubble = new MessageBubble(sender, vidFile.getName(), false, settings, curTargetLang,
                            ChatMessageModel.MessageType.VIDEO, vidFile, senderRole);
                    activeMessageBubbles.add(bubble);
                    chatBox.getChildren().add(bubble);
                    scrollPane.setVvalue(1.0);
                });
            }

            @Override
            public void onFileOfferReceived(String sender, String fileName, long fileSize) {
                Platform.runLater(() -> appendFileOfferRow(sender, fileName, fileSize));
            }

            @Override
            public void onFileDownloadProgress(String fileName, long downloaded, long total, double speedKBps, int etaSeconds) {}

            @Override
            public void onFileSaved(String fileName, String path) {}

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> showAlert(I18nService.getLanguage().equals("vi") ? "Lỗi P2P" : "P2P Error", errorMessage));
            }
        };

        P2PListener p2pServer = new P2PListener(myP2PPort, uiCallback);
        p2pServer.start();
    }

    private void setupUI() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #1E1E2E;");
        mainLayout.setPadding(new Insets(10));

        // ── 1. Top Bar ────────────────────────────────────────────────────────
        mainLayout.setTop(buildTopBar());

        // ── 2. Left Sidebar (User list with search & location & role badges) ──
        mainLayout.setLeft(buildLeftSidebar());
        BorderPane.setMargin(mainLayout.getLeft(), new Insets(0, 10, 0, 0));

        // ── 3. Center Area (Chat Box & Multimedia Toolbar) ────────────────────
        mainLayout.setCenter(buildCenterChatArea());

        Scene scene = new Scene(mainLayout, 1020, 680);
        primaryStage.setScene(scene);
        updateInterfaceTexts();
        primaryStage.setOnCloseRequest(e -> {
            try {
                if (centralServerSocket != null && !centralServerSocket.isClosed()) centralServerSocket.close();
            } catch (IOException ignored) {}
            System.exit(0);
        });
        primaryStage.show();
    }

    private HBox buildTopBar() {
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 14, 10, 14));
        topBar.setStyle("-fx-background-color: #181825; -fx-background-radius: 10; -fx-border-color: #313244; -fx-border-width: 0 0 1 0;");

        lblMyInfo.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblMyInfo.setTextFill(Color.web("#CBA6F7"));
        updateMyInfoBadge();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Nút Cài đặt
        btnSettings = new Button(I18nService.get("btn.settings"));
        btnSettings.setStyle(buttonStyle("#313244", "#CDD6F4"));
        btnSettings.setOnAction(e -> openSettings());

        topBar.getChildren().addAll(lblMyInfo, spacer, btnSettings);
        return topBar;
    }

    private VBox buildLeftSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPrefWidth(290);
        sidebar.setPadding(new Insets(10));
        sidebar.setStyle("-fx-background-color: #181825; -fx-background-radius: 10; -fx-border-color: #313244; -fx-border-width: 1;");

        lblSidebarTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblSidebarTitle.setTextFill(Color.web("#A6E3A1"));

        // Ô tìm kiếm user theo tên
        txtSearchUser.setPromptText(I18nService.get("search_user_prompt"));
        txtSearchUser.setStyle("-fx-background-color: #24273A; -fx-text-fill: #CDD6F4; -fx-border-color: #45475A; -fx-border-radius: 6; -fx-background-radius: 6;");

        filteredUsers = new FilteredList<>(rawOnlineUsers, p -> true);
        txtSearchUser.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredUsers.setPredicate(userStr -> {
                if (newVal == null || newVal.trim().isEmpty()) return true;
                String lower = newVal.toLowerCase();
                String name = userStr.split(" - ")[0].toLowerCase();
                return name.contains(lower);
            });
        });

        userListView.setItems(filteredUsers);
        userListView.setStyle("-fx-background-color: #1E1E2E; -fx-control-inner-background: #1E1E2E; -fx-border-color: #313244; -fx-border-radius: 6; -fx-background-radius: 6;");
        VBox.setVgrow(userListView, Priority.ALWAYS);

        // Custom Cell: KHÔNG hiển thị IP công khai, hiển thị Avatar, Username, Badge Phân quyền và Vị trí
        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    String[] parts = item.split(" - ");
                    String name = parts[0];
                    String loc = parts.length > 2 ? parts[2] : "📍 Unknown location";
                    String roleStr = parts.length > 3 ? parts[3] : "USER";
                    UserRole role = UserRole.fromString(roleStr);

                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(6, 4, 6, 4));

                    StackPane avatar = new StackPane();
                    Circle circle = new Circle(14, Color.web("#45475A"));
                    Label initial = new Label(name.substring(0, Math.min(1, name.length())).toUpperCase());
                    initial.setTextFill(Color.web("#CDD6F4"));
                    initial.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
                    avatar.getChildren().addAll(circle, initial);

                    VBox info = new VBox(2);
                    HBox nameRow = new HBox(6);
                    nameRow.setAlignment(Pos.CENTER_LEFT);

                    Label lblName = new Label(name);
                    lblName.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
                    lblName.setTextFill(Color.web("#CDD6F4"));

                    Label lblRole = new Label(role.getDisplayName());
                    lblRole.setFont(Font.font("SansSerif", FontWeight.BOLD, 9));
                    lblRole.setTextFill(Color.web(role.getColorHex()));
                    lblRole.setStyle("-fx-background-color: #181825; -fx-padding: 1 4 1 4; -fx-background-radius: 3;");

                    nameRow.getChildren().addAll(lblName, lblRole);

                    Label lblLocBadge = new Label(loc);
                    lblLocBadge.setFont(Font.font("SansSerif", 10));
                    lblLocBadge.setTextFill(Color.web("#89DCEB"));
                    lblLocBadge.setStyle("-fx-background-color: #1E1E2E; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");

                    info.getChildren().addAll(nameRow, lblLocBadge);
                    HBox.setHgrow(info, Priority.ALWAYS);

                    Circle onlineDot = new Circle(4, Color.web("#A6E3A1"));

                    row.getChildren().addAll(avatar, info, onlineDot);
                    setGraphic(row);
                    setText(null);
                    setStyle("-fx-background-color: #24273A; -fx-background-radius: 6; -fx-padding: 2;");
                }
            }
        });

        // Khi click chọn user: Tải lịch sử tin nhắn từ MongoDB
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String[] parts = newVal.split(" - ");
                targetUsername = parts[0];
                String[] ipPort = parts[1].split(":");
                targetIP = ipPort[0];
                targetP2PPort = Integer.parseInt(ipPort[1]);
                targetLocationDisplay = parts.length > 2 ? parts[2] : "📍 Unknown location";
                targetRole = parts.length > 3 ? UserRole.fromString(parts[3]) : UserRole.USER;

                updateChatHeader();
                loadChatHistory();
            }
        });

        sidebar.getChildren().addAll(lblSidebarTitle, txtSearchUser, userListView);
        return sidebar;
    }

    private VBox buildCenterChatArea() {
        VBox centerArea = new VBox(8);

        // ── Conversation Header ───────────────────────────────────────────────
        HBox chatHeader = new HBox(10);
        chatHeader.setAlignment(Pos.CENTER_LEFT);
        chatHeader.setPadding(new Insets(8, 14, 8, 14));
        chatHeader.setStyle("-fx-background-color: #181825; -fx-background-radius: 10; -fx-border-color: #313244; -fx-border-width: 1;");

        VBox targetInfoBox = new VBox(2);
        HBox targetNameRow = new HBox(6);
        targetNameRow.setAlignment(Pos.CENTER_LEFT);

        lblChatHeaderUser.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
        lblChatHeaderUser.setTextFill(Color.web("#CDD6F4"));

        lblChatHeaderRole.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
        lblChatHeaderRole.setStyle("-fx-background-color: #24273A; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");

        targetNameRow.getChildren().addAll(lblChatHeaderUser, lblChatHeaderRole);

        lblChatHeaderLocation.setFont(Font.font("SansSerif", 11));
        lblChatHeaderLocation.setTextFill(Color.web("#89DCEB"));
        targetInfoBox.getChildren().addAll(targetNameRow, lblChatHeaderLocation);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Bộ chọn nhanh ngôn ngữ dịch
        Label lblTransTo = new Label(I18nService.get("target_lang_label"));
        lblTransTo.setTextFill(Color.web("#A6ADC8"));
        lblTransTo.setFont(Font.font("SansSerif", 11));

        cbChatTargetLang = new ComboBox<>();
        cbChatTargetLang.getItems().addAll(LanguageItem.SUPPORTED_LANGUAGES);
        cbChatTargetLang.setValue(LanguageItem.findByCode(settings.getDefaultTargetLanguage()));
        cbChatTargetLang.setStyle("-fx-background-color: #24273A; -fx-text-fill: #CDD6F4; -fx-font-size: 11px; -fx-border-color: #45475A; -fx-border-radius: 6;");

        cbChatTargetLang.setOnAction(e -> {
            LanguageItem selected = cbChatTargetLang.getValue();
            if (selected != null && targetUsername != null) {
                settings.setTargetLanguageForUser(targetUsername, selected.getCode());
                for (MessageBubble bubble : activeMessageBubbles) {
                    bubble.retranslate(selected.getCode());
                }
            }
        });

        // Nút chuyển nhanh Auto Translate ON/OFF
        btnQuickAutoTranslate = new Button();
        updateAutoTranslateButtonText();
        btnQuickAutoTranslate.setOnAction(e -> {
            settings.setAutoTranslate(!settings.isAutoTranslate());
            settings.save();
            updateAutoTranslateButtonText();
        });

        chatHeader.getChildren().addAll(targetInfoBox, spacer, lblTransTo, cbChatTargetLang, btnQuickAutoTranslate);

        // ── Khung tin nhắn (Message list) ─────────────────────────────────────
        chatBox.setPadding(new Insets(10));
        scrollPane.setContent(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #181825; -fx-background-color: #181825; -fx-border-color: #313244; -fx-border-radius: 10;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // ── Thanh công cụ gửi tin nhắn & đa phương tiện ───────────────────────
        VBox inputContainer = new VBox(6);

        HBox mediaToolbar = new HBox(8);
        mediaToolbar.setAlignment(Pos.CENTER_LEFT);

        btnEmoji = new Button("😊 Emoji");
        btnEmoji.setStyle(buttonStyle("#313244", "#CDD6F4"));
        emojiPicker = new EmojiPickerPopup(txtMessage);
        btnEmoji.setOnAction(e -> emojiPicker.show(btnEmoji));

        btnVoice = new Button("🎙 Ghi âm");
        btnVoice.setStyle(buttonStyle("#45475A", "#CDD6F4"));
        btnVoice.setOnAction(e -> toggleVoiceRecording());

        btnSendImage = new Button("🖼️ Gửi Ảnh");
        btnSendImage.setStyle(buttonStyle("#89DCEB", "#1E1E2E"));
        btnSendImage.setOnAction(e -> openImageChooserAndSend());

        btnSendVideo = new Button("🎬 Gửi Video");
        btnSendVideo.setStyle(buttonStyle("#CBA6F7", "#1E1E2E"));
        btnSendVideo.setOnAction(e -> openVideoChooserAndSend());

        btnSendFile = new Button(I18nService.get("btn.send_file"));
        btnSendFile.setStyle(buttonStyle("#F9E2AF", "#1E1E2E"));
        btnSendFile.setOnAction(e -> openFileChooserAndSend());

        mediaToolbar.getChildren().addAll(btnEmoji, btnVoice, btnSendImage, btnSendVideo, btnSendFile);

        HBox textInputBar = new HBox(8);
        textInputBar.setAlignment(Pos.CENTER);

        txtMessage.setPromptText(I18nService.getLanguage().equals("vi") ? "Nhập tin nhắn..." : "Type a message...");
        txtMessage.setStyle("-fx-background-color: #24273A; -fx-text-fill: #CDD6F4; -fx-font-size: 13px; -fx-border-color: #45475A; -fx-border-radius: 8; -fx-padding: 8;");
        HBox.setHgrow(txtMessage, Priority.ALWAYS);

        btnSend = new Button("✈️ " + I18nService.get("btn.send"));
        btnSend.setStyle(buttonStyle("#89B4FA", "#1E1E2E"));
        btnSend.setOnAction(e -> sendTextMessage());
        txtMessage.setOnAction(e -> sendTextMessage());

        textInputBar.getChildren().addAll(txtMessage, btnSend);
        inputContainer.getChildren().addAll(mediaToolbar, textInputBar);

        lblStatus.setFont(Font.font("SansSerif", 11));
        lblStatus.setTextFill(Color.web("#A6ADC8"));

        centerArea.getChildren().addAll(chatHeader, scrollPane, inputContainer, lblStatus);
        return centerArea;
    }

    private void updateChatHeader() {
        if (targetUsername != null) {
            lblChatHeaderUser.setText(targetUsername);
            lblChatHeaderLocation.setText(targetLocationDisplay);
            lblChatHeaderRole.setText(targetRole.getDisplayName());
            lblChatHeaderRole.setTextFill(Color.web(targetRole.getColorHex()));
            lblStatus.setText(I18nService.get("status.chatting_with") + " " + targetUsername);

            String chatLang = settings.getTargetLanguageForUser(targetUsername);
            cbChatTargetLang.setValue(LanguageItem.findByCode(chatLang));
        } else {
            lblChatHeaderUser.setText(I18nService.get("status.waiting"));
            lblChatHeaderLocation.setText("");
            lblChatHeaderRole.setText("");
        }
    }

    private void updateAutoTranslateButtonText() {
        if (settings.isAutoTranslate()) {
            btnQuickAutoTranslate.setText(I18nService.get("btn.auto_translate_on"));
            btnQuickAutoTranslate.setStyle(buttonStyle("#A6E3A1", "#1E1E2E"));
        } else {
            btnQuickAutoTranslate.setText(I18nService.get("btn.auto_translate_off"));
            btnQuickAutoTranslate.setStyle(buttonStyle("#6C7086", "#CDD6F4"));
        }
    }

    private String getActiveConversationTargetLang() {
        if (targetUsername != null) {
            return settings.getTargetLanguageForUser(targetUsername);
        }
        return settings.getDefaultTargetLanguage();
    }

    // ── Tải lịch sử trò chuyện từ MongoDB / Lưu trữ cục bộ ─────────────────
    private void loadChatHistory() {
        if (targetUsername == null) return;
        chatBox.getChildren().clear();
        activeMessageBubbles.clear();

        new Thread(() -> {
            List<ChatMessageModel> history = MongoDbService.getChatHistory(username, targetUsername, 40);
            Platform.runLater(() -> {
                for (ChatMessageModel m : history) {
                    boolean isMine = m.getSender().equalsIgnoreCase(username);
                    UserRole senderRole = isMine ? myRole : targetRole;
                    String curTargetLang = getActiveConversationTargetLang();

                    File media = null;
                    if (m.getFileName() != null && !m.getFileName().isEmpty()) {
                        media = new File(m.getFileName());
                    }

                    MessageBubble bubble = new MessageBubble(m.getSender(), m.getContent(), isMine, settings, curTargetLang,
                            m.getType(), media, senderRole);
                    activeMessageBubbles.add(bubble);
                    chatBox.getChildren().add(bubble);
                }
                scrollPane.setVvalue(1.0);
            });
        }).start();
    }

    // ── Gửi tin nhắn TEXT ──────────────────────────────────────────────────
    private void sendTextMessage() {
        String text = txtMessage.getText().trim();
        if (text.isEmpty() || targetIP == null) return;

        txtMessage.clear();

        String curTargetLang = getActiveConversationTargetLang();
        MessageBubble bubble = new MessageBubble("Tôi", text, true, settings, curTargetLang,
                ChatMessageModel.MessageType.TEXT, null, myRole);
        activeMessageBubbles.add(bubble);
        chatBox.getChildren().add(bubble);
        scrollPane.setVvalue(1.0);

        final String finalTargetIP = targetIP;
        final int finalTargetPort = targetP2PPort;
        final String finalTargetUser = targetUsername;

        new Thread(() -> {
            try {
                P2PClient.sendMessage(finalTargetIP, finalTargetPort, username, text);
                sendLogMsg(finalTargetUser != null ? finalTargetUser : finalTargetIP, text);
            } catch (IOException e) {
                Platform.runLater(() -> showAlert(I18nService.getLanguage().equals("vi") ? "Lỗi P2P" : "P2P Error",
                        (I18nService.getLanguage().equals("vi") ? "Không thể gửi tới " : "Failed to send to ") + finalTargetIP));
            }
        }).start();
    }

    // ── Gửi HÌNH ẢNH (Không cần tải về, hiển thị trực tiếp) ────────────────
    private void openImageChooserAndSend() {
        if (targetIP == null) {
            showAlert("Thông báo", "Vui lòng chọn người nhận trước!");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn hình ảnh để gửi");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        File file = chooser.showOpenDialog(primaryStage);

        if (file != null) {
            MessageBubble bubble = new MessageBubble("Tôi", file.getName(), true, settings,
                    getActiveConversationTargetLang(), ChatMessageModel.MessageType.IMAGE, file, myRole);
            activeMessageBubbles.add(bubble);
            chatBox.getChildren().add(bubble);
            scrollPane.setVvalue(1.0);

            final String fIp = targetIP;
            final int fPort = targetP2PPort;
            final String fUser = targetUsername;

            new Thread(() -> {
                try {
                    P2PClient.sendImage(fIp, fPort, username, file);
                    sendLogMedia(fUser, "IMAGE", file.getName(), file.length());
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert("Lỗi gửi ảnh", e.getMessage()));
                }
            }).start();
        }
    }

    // ── Ghi âm & Gửi TIN NHẮN THOẠI ────────────────────────────────────────
    private void toggleVoiceRecording() {
        if (targetIP == null) {
            showAlert("Thông báo", "Vui lòng chọn người nhận trước!");
            return;
        }

        if (!AudioRecordService.isRecording()) {
            btnVoice.setText("🔴 Dừng & Gửi Ghi Âm");
            btnVoice.setStyle(buttonStyle("#F38BA8", "#1E1E2E"));
            AudioRecordService.startRecording(new AudioRecordService.AudioRecordCallback() {
                @Override
                public void onRecordingStarted() {
                    Platform.runLater(() -> lblStatus.setText("🎙 Đang ghi âm cuộc trò chuyện..."));
                }

                @Override
                public void onRecordingFinished(File audioFile, int durationSec) {
                    Platform.runLater(() -> {
                        btnVoice.setText("🎙 Ghi âm");
                        btnVoice.setStyle(buttonStyle("#45475A", "#CDD6F4"));
                        lblStatus.setText("✅ Đã hoàn tất ghi âm (" + durationSec + "s)");

                        MessageBubble bubble = new MessageBubble("Tôi", durationSec + "s", true, settings,
                                getActiveConversationTargetLang(), ChatMessageModel.MessageType.VOICE, audioFile, myRole);
                        activeMessageBubbles.add(bubble);
                        chatBox.getChildren().add(bubble);
                        scrollPane.setVvalue(1.0);
                    });

                    final String fIp = targetIP;
                    final int fPort = targetP2PPort;
                    final String fUser = targetUsername;

                    new Thread(() -> {
                        try {
                            P2PClient.sendVoice(fIp, fPort, username, durationSec, audioFile);
                            sendLogMedia(fUser, "VOICE", audioFile.getName(), audioFile.length());
                        } catch (IOException e) {
                            Platform.runLater(() -> showAlert("Lỗi gửi voice", e.getMessage()));
                        }
                    }).start();
                }

                @Override
                public void onError(String message) {
                    Platform.runLater(() -> {
                        btnVoice.setText("🎙 Ghi âm");
                        btnVoice.setStyle(buttonStyle("#45475A", "#CDD6F4"));
                        showAlert("Lỗi ghi âm", message);
                    });
                }
            });
        } else {
            AudioRecordService.stopRecording();
        }
    }

    // ── Gửi VIDEO (Có thể phát trực tiếp) ──────────────────────────────────
    private void openVideoChooserAndSend() {
        if (targetIP == null) {
            showAlert("Thông báo", "Vui lòng chọn người nhận trước!");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn Video để gửi");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video", "*.mp4", "*.m4v", "*.mov", "*.avi", "*.mkv"));
        File file = chooser.showOpenDialog(primaryStage);

        if (file != null) {
            MessageBubble bubble = new MessageBubble("Tôi", file.getName(), true, settings,
                    getActiveConversationTargetLang(), ChatMessageModel.MessageType.VIDEO, file, myRole);
            activeMessageBubbles.add(bubble);
            chatBox.getChildren().add(bubble);
            scrollPane.setVvalue(1.0);

            final String fIp = targetIP;
            final int fPort = targetP2PPort;
            final String fUser = targetUsername;

            new Thread(() -> {
                try {
                    P2PClient.sendVideo(fIp, fPort, username, file);
                    sendLogMedia(fUser, "VIDEO", file.getName(), file.length());
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert("Lỗi gửi video", e.getMessage()));
                }
            }).start();
        }
    }

    // ── Gửi TẬP TIN THÔNG THƯỜNG ───────────────────────────────────────────
    private void openFileChooserAndSend() {
        if (targetIP == null) {
            showAlert(I18nService.getLanguage().equals("vi") ? "Cảnh báo" : "Warning",
                    I18nService.getLanguage().equals("vi") ? "Vui lòng chọn người nhận từ danh sách bên trái!" : "Please select a recipient first!");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile != null) {
            offeredFiles.put(selectedFile.getName(), selectedFile.getAbsolutePath());
            appendFileOfferRow("Tôi", selectedFile.getName(), selectedFile.length());

            final String finalTargetIP = targetIP;
            final int finalTargetPort = targetP2PPort;
            final String finalTargetUser = targetUsername;

            new Thread(() -> {
                try {
                    P2PClient.sendFileOffer(finalTargetIP, finalTargetPort, username, selectedFile);
                    sendLogFile(finalTargetUser != null ? finalTargetUser : finalTargetIP,
                            selectedFile.getName(), selectedFile.length());
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert(I18nService.getLanguage().equals("vi") ? "Lỗi P2P" : "P2P Error",
                            I18nService.getLanguage().equals("vi") ? "Truyền thông báo file thất bại!" : "File offer failed!"));
                }
            }).start();
        }
    }

    private void appendFileOfferRow(String sender, String fileName, long fileSize) {
        boolean isMine = sender.equals("Tôi") || sender.equals(username);

        HBox outer = new HBox();
        outer.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        outer.setPadding(new Insets(4, 10, 4, 10));

        VBox card = new VBox(6);
        card.setMaxWidth(480);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle(isMine
                ? "-fx-background-color: #1E3A3A; -fx-background-radius: 12; -fx-border-color: #315858; -fx-border-width: 1;"
                : "-fx-background-color: #24273A; -fx-background-radius: 12; -fx-border-color: #363A4F; -fx-border-width: 1;");

        Label lblFileTitle = new Label("📁 " + fileName + " (" + formatSize(fileSize) + ")");
        lblFileTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblFileTitle.setTextFill(Color.web("#F9E2AF"));

        if (!isMine) {
            Button btnDownload = new Button(I18nService.get("btn.download") + " " + fileName);
            btnDownload.setStyle(buttonStyle("#A6E3A1", "#1E1E2E"));

            ProgressBar bar = new ProgressBar(0);
            bar.setPrefWidth(200);
            Label lblInfo = new Label();
            lblInfo.setFont(Font.font("SansSerif", 11));
            lblInfo.setTextFill(Color.web("#89DCEB"));

            btnDownload.setOnAction(e -> {
                String actualIP = null;
                int actualPort = 0;
                for (String user : rawOnlineUsers) {
                    if (user.startsWith(sender + " - ")) {
                        String[] parts = user.split(" - ")[1].split(":");
                        actualIP = parts[0];
                        actualPort = Integer.parseInt(parts[1]);
                        break;
                    }
                }

                if (actualIP == null) {
                    showAlert("Lỗi", "Người gửi " + sender + " hiện không online, không thể tải file!");
                    return;
                }

                btnDownload.setDisable(true);
                btnDownload.setText(I18nService.get("downloading"));
                startFileDownload(actualIP, actualPort, fileName, bar, lblInfo, btnDownload);
            });

            HBox downloadRow = new HBox(8, btnDownload, bar, lblInfo);
            downloadRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().addAll(lblFileTitle, downloadRow);
        } else {
            card.getChildren().add(lblFileTitle);
        }

        outer.getChildren().add(card);
        chatBox.getChildren().add(outer);
        scrollPane.setVvalue(1.0);
    }

    private void startFileDownload(String ip, int port, String fileName, ProgressBar bar, Label lblInfo, Button btnDownload) {
        new Thread(() -> {
            try (Socket s = new Socket(ip, port);
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                 DataInputStream dis = new DataInputStream(s.getInputStream())) {

                dos.writeUTF("DOWNLOAD_REQ");
                dos.writeUTF(fileName);
                dos.flush();

                String resp = dis.readUTF();
                if ("FILE_DATA".equals(resp)) {
                    String fn = dis.readUTF();
                    long sz = dis.readLong();

                    File dir = new File("downloads");
                    if (!dir.exists()) dir.mkdirs();
                    File saveFile = new File(dir, fn);

                    byte[] buffer = new byte[4096];
                    long total = 0;
                    long start = System.currentTimeMillis();

                    try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                        int r;
                        while (total < sz && (r = dis.read(buffer, 0, (int) Math.min(buffer.length, sz - total))) > 0) {
                            fos.write(buffer, 0, r);
                            total += r;
                            long now = System.currentTimeMillis();
                            long ele = now - start;
                            if (ele > 0) {
                                double speed = (total / 1024.0) / (ele / 1000.0);
                                int eta = (int) (((sz - total) / 1024.0) / Math.max(speed, 0.1));
                                final double finalSpd = speed;
                                final int finalEta = eta;
                                final long finalTot = total;

                                Platform.runLater(() -> {
                                    bar.setProgress(Math.min((double) finalTot / sz, 1.0));
                                    lblInfo.setText(String.format("%.1f KB/s | %ds", finalSpd, finalEta));
                                });
                            }
                        }
                    }
                    Platform.runLater(() -> {
                        bar.setProgress(1.0);
                        lblInfo.setText("✅ " + I18nService.get("file.saved_to") + " " + saveFile.getAbsolutePath());
                        btnDownload.setText("✅ Xong");
                    });
                } else {
                    Platform.runLater(() -> showAlert(I18nService.getLanguage().equals("vi") ? "Lỗi tải file" : "Download Error",
                            I18nService.get("file.error_not_found")));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert("Lỗi kết nối", ex.getMessage()));
            }
        }).start();
    }

    private void openSettings() {
        SettingsDialog.show(primaryStage, settings, myLocation, updatedSettings -> {
            this.settings = updatedSettings;
            I18nService.setLanguage(settings.getInterfaceLanguage());
            updateInterfaceTexts();
            updateChatHeader();
            updateAutoTranslateButtonText();

            GeoLocationService.resolveCurrentLocation(settings).thenAccept(loc -> {
                this.myLocation = loc;
                Platform.runLater(this::updateMyInfoBadge);
            });
        });
    }

    private void updateInterfaceTexts() {
        primaryStage.setTitle(I18nService.get("app.title") + " - " + username + " (Port: " + myP2PPort + ")");
        lblSidebarTitle.setText(I18nService.get("online_users") + " (" + rawOnlineUsers.size() + ")");
        txtSearchUser.setPromptText(I18nService.get("search_user_prompt"));
        btnSend.setText(I18nService.get("btn.send"));
        btnSendFile.setText(I18nService.get("btn.send_file"));
        btnSettings.setText(I18nService.get("btn.settings"));
        txtMessage.setPromptText(I18nService.getLanguage().equals("vi") ? "Nhập tin nhắn..." : "Type a message...");
        updateMyInfoBadge();
    }

    private synchronized void sendLogMsg(String toUser, String message) {
        try {
            if (centralServerDos != null) {
                centralServerDos.writeUTF("LOG_MSG");
                centralServerDos.writeUTF(toUser);
                centralServerDos.writeUTF(message);
                centralServerDos.flush();
            }
        } catch (IOException ignored) {}
    }

    private synchronized void sendLogFile(String toUser, String fileName, long fileSize) {
        try {
            if (centralServerDos != null) {
                centralServerDos.writeUTF("LOG_FILE");
                centralServerDos.writeUTF(toUser);
                centralServerDos.writeUTF(fileName);
                centralServerDos.writeLong(fileSize);
                centralServerDos.flush();
            }
        } catch (IOException ignored) {}
    }

    private synchronized void sendLogMedia(String toUser, String type, String mediaName, long size) {
        try {
            if (centralServerDos != null) {
                centralServerDos.writeUTF("LOG_MEDIA");
                centralServerDos.writeUTF(toUser);
                centralServerDos.writeUTF(type);
                centralServerDos.writeUTF(mediaName);
                centralServerDos.writeLong(size);
                centralServerDos.flush();
            }
        } catch (IOException ignored) {}
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024));
    }

    private String buttonStyle(String bgColor, String fgColor) {
        return "-fx-background-color: " + bgColor + "; "
                + "-fx-text-fill: " + fgColor + "; "
                + "-fx-font-weight: bold; "
                + "-fx-font-size: 11px; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 6 12 6 12; "
                + "-fx-cursor: hand;";
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
