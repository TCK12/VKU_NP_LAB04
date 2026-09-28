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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.lab04.network.INetworkCallback;
import org.example.lab04.network.P2PClient;
import org.example.lab04.network.P2PListener;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client UI – Dark theme, hỗ trợ gửi ảnh tiết kiệm băng thông (kiểu Telegram).
 */
public class ChatClientApp extends Application {

    // ── Shared state (dùng bởi P2PListener) ───────────────────────────────────
    public static final Map<String, String> offeredFiles = new ConcurrentHashMap<>();

    // ── Image extensions ──────────────────────────────────────────────────────
    private static final Set<String> IMG_EXTS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ── Màu palette (Catppuccin Mocha) ────────────────────────────────────────
    private static final String BG        = "#1E1E2E";
    private static final String SURFACE   = "#181825";
    private static final String CARD      = "#313244";
    private static final String CARD_ME   = "#3D3560";
    private static final String TEXT      = "#CDD6F4";
    private static final String TEXT_SUB  = "#7F849C";
    private static final String ACCENT    = "#CBA6F7";
    private static final String GREEN     = "#A6E3A1";
    private static final String BLUE      = "#89B4FA";
    private static final String YELLOW    = "#F9E2AF";
    private static final String RED       = "#F38BA8";
    private static final String BORDER    = "#3A3A5C";

    // ── User address map (thread-safe) ────────────────────────────────────────
    private final Map<String, String[]> userAddressMap = new ConcurrentHashMap<>(); // name → [ip, port]

    // ── UI fields ─────────────────────────────────────────────────────────────
    private Stage primaryStage;
    private Label lblChatHeader;
    private Label lblOnlineCount;
    private TextField searchField;
    private ObservableList<String> allUsers = FXCollections.observableArrayList();
    private FilteredList<String> filteredUsers;
    private ListView<String> userListView;
    private VBox chatBox;
    private ScrollPane scrollPane;
    private TextField txtMessage;
    private ProgressBar downloadProgress;
    private Label lblDownloadInfo;

    // ── Session state ──────────────────────────────────────────────────────────
    private String username;
    private int myP2PPort;
    private String serverIP = "127.0.0.1";
    private int serverPort  = 9999;
    private String targetIP       = null;
    private String targetUsername = null;
    private int    targetP2PPort  = 0;
    private Socket centralServerSocket;
    private DataOutputStream centralServerDos;

    // ── Video Call state ───────────────────────────────────────────────────────
    private int myUdpPort;
    private CallWindow currentCallWindow;

    // ─────────────────────────────────────────────────────────────────────────
    //  JavaFX entry
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        if (!showLoginDialog()) { Platform.exit(); return; }
        buildUI(stage);
        startP2PListener();
        connectCentralServer();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI Build
    // ─────────────────────────────────────────────────────────────────────────

    private void buildUI(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setTop(buildTopBar());
        root.setLeft(buildLeftPanel());
        root.setCenter(buildChatArea());

        stage.setScene(new Scene(root, 1060, 700));
        stage.setTitle("P2P Chat  ·  " + username);
        stage.setMinWidth(800);
        stage.setMinHeight(520);
        stage.setOnCloseRequest(e -> {
            try { if (centralServerSocket != null) centralServerSocket.close(); } catch (IOException ignored) {}
            Platform.exit();
        });
        stage.show();
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox(14);
        bar.setPadding(new Insets(12, 20, 12, 20));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: " + SURFACE + "; " +
                     "-fx-border-color: " + BORDER + "; -fx-border-width: 0 0 1 0;");

        Label logo = new Label("💬  P2P Chat");
        logo.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
        logo.setTextFill(Color.web(ACCENT));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Avatar chip
        String color = avatarColor(username);
        Circle dot = new Circle(5, Color.web(GREEN));
        Label chipName = new Label(username + "   ·   Port " + myP2PPort);
        chipName.setFont(Font.font("SansSerif", 13));
        chipName.setTextFill(Color.web(TEXT));

        Circle av = new Circle(16, Color.web(color));
        Label avLetter = new Label(initial(username));
        avLetter.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        avLetter.setTextFill(Color.WHITE);
        StackPane avPane = new StackPane(av, avLetter);

        HBox chip = new HBox(8, avPane, chipName, dot, label("Online", GREEN, 12));
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(6, 14, 6, 10));
        chip.setStyle("-fx-background-color: " + CARD + "; -fx-background-radius: 20;");

        bar.getChildren().addAll(logo, spacer, chip);
        return bar;
    }

    // ── Left panel (user list + search) ──────────────────────────────────────

    private VBox buildLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(268);
        panel.setMinWidth(220);
        panel.setPadding(new Insets(14, 10, 14, 14));
        panel.setStyle("-fx-background-color: " + SURFACE + "; " +
                       "-fx-border-color: " + BORDER + "; -fx-border-width: 0 1 0 0;");

        lblOnlineCount = new Label("🟢  Online (0)");
        lblOnlineCount.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblOnlineCount.setTextFill(Color.web(GREEN));

        // Search
        searchField = new TextField();
        searchField.setPromptText("🔍  Tìm theo tên...");
        searchField.setStyle(fieldStyle());

        filteredUsers = new FilteredList<>(allUsers, s -> true);
        searchField.textProperty().addListener((obs, o, n) -> {
            String q = n.trim().toLowerCase();
            filteredUsers.setPredicate(u -> q.isEmpty() || u.toLowerCase().contains(q));
        });

        userListView = new ListView<>(filteredUsers);
        userListView.setStyle("-fx-background-color: " + BG + "; " +
                              "-fx-control-inner-background: " + BG + "; " +
                              "-fx-border-color: transparent;");
        VBox.setVgrow(userListView, Priority.ALWAYS);
        userListView.setCellFactory(lv -> new UserCell());

        userListView.getSelectionModel().selectedItemProperty().addListener((obs, old, nw) -> {
            if (nw == null) return;
            String[] parts = nw.split(" - ");
            targetUsername = parts[0].trim();
            String[] ipPort = parts[1].trim().split(":");
            targetIP       = ipPort[0];
            targetP2PPort  = Integer.parseInt(ipPort[1]);
            lblChatHeader.setText("💬  " + targetUsername);
            chatBox.getChildren().clear();
            appendSystem("Bắt đầu chat với " + targetUsername);
        });

        panel.getChildren().addAll(lblOnlineCount, searchField, userListView);
        return panel;
    }

    // ── Chat area ─────────────────────────────────────────────────────────────

    private VBox buildChatArea() {
        // Header
        HBox header = new HBox();
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + SURFACE + "; " +
                        "-fx-border-color: " + BORDER + "; -fx-border-width: 0 0 1 0;");

        lblChatHeader = new Label("Chọn người dùng để bắt đầu...");
        lblChatHeader.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        lblChatHeader.setTextFill(Color.web(TEXT));
        
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        
        Button btnCall = new Button("📞 Gọi Video");
        btnCall.setStyle("-fx-background-color: " + GREEN + "; -fx-text-fill: #1E1E2E; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;");
        btnCall.setOnAction(e -> initiateVideoCall());
        
        header.getChildren().addAll(lblChatHeader, headerSpacer, btnCall);

        // Messages
        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(14));

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + "; " +
                            "-fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Progress bar (ẩn khi không tải)
        downloadProgress = new ProgressBar(0);
        downloadProgress.setPrefWidth(Double.MAX_VALUE);
        downloadProgress.setVisible(false);
        downloadProgress.setStyle("-fx-accent: " + ACCENT + ";");

        lblDownloadInfo = new Label("");
        lblDownloadInfo.setFont(Font.font("SansSerif", 11));
        lblDownloadInfo.setTextFill(Color.web(TEXT_SUB));
        lblDownloadInfo.setPadding(new Insets(0, 0, 2, 0));

        VBox progressRow = new VBox(3, downloadProgress, lblDownloadInfo);
        progressRow.setPadding(new Insets(0, 12, 0, 12));

        // Input
        HBox inputBar = buildInputBar();

        VBox area = new VBox(header, scrollPane, progressRow, inputBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return area;
    }

    private HBox buildInputBar() {
        txtMessage = new TextField();
        txtMessage.setPromptText("Nhập tin nhắn...");
        txtMessage.setStyle("-fx-background-color: " + CARD + "; -fx-text-fill: " + TEXT + "; " +
                            "-fx-prompt-text-fill: " + TEXT_SUB + "; " +
                            "-fx-border-color: " + BORDER + "; -fx-border-radius: 22; " +
                            "-fx-background-radius: 22; -fx-padding: 10 16 10 16; -fx-font-size: 13px;");
        HBox.setHgrow(txtMessage, Priority.ALWAYS);
        txtMessage.setOnAction(e -> onSendText());

        Button btnSend = roundButton("▶", ACCENT, BG, 40);
        btnSend.setOnAction(e -> onSendText());

        Button btnImg  = iconBtn("🖼", "Gửi ảnh (tiết kiệm băng thông)");
        Button btnFile = iconBtn("📁", "Gửi file");
        btnImg.setOnAction(e -> onSendImage());
        btnFile.setOnAction(e -> onSendFile());

        HBox bar = new HBox(8, btnImg, btnFile, txtMessage, btnSend);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.setAlignment(Pos.CENTER);
        bar.setStyle("-fx-background-color: " + SURFACE + "; " +
                     "-fx-border-color: " + BORDER + "; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    // ── Custom user list cell ─────────────────────────────────────────────────

    private class UserCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); setStyle("-fx-background-color: transparent;"); return; }

            String[] parts = item.split(" - ");
            String name = parts[0].trim();
            String addr = parts.length > 1 ? parts[1].trim() : "";

            // Avatar
            Circle av = new Circle(22, Color.web(avatarColor(name)));
            Label letter = new Label(initial(name));
            letter.setFont(Font.font("SansSerif", FontWeight.BOLD, 14));
            letter.setTextFill(Color.WHITE);

            Circle onlineDot = new Circle(6, Color.web(GREEN));
            StackPane dotWrap = new StackPane(onlineDot);
            dotWrap.setAlignment(Pos.BOTTOM_RIGHT);
            dotWrap.setPadding(new Insets(24, 0, 0, 24));

            StackPane avPane = new StackPane(av, letter, dotWrap);
            avPane.setMinWidth(48); avPane.setPrefWidth(48);

            Label lName = new Label(name);
            lName.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
            lName.setTextFill(Color.web(TEXT));

            Label lAddr = new Label("🌐 " + addr);
            lAddr.setFont(Font.font("Consolas", 11));
            lAddr.setTextFill(Color.web(TEXT_SUB));

            VBox txt = new VBox(3, lName, lAddr);

            HBox row = new HBox(10, avPane, txt);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(9, 8, 9, 6));
            row.setStyle("-fx-background-color: " + (isSelected() ? CARD : "#252535") +
                         "; -fx-background-radius: 10; -fx-cursor: hand;");

            setGraphic(row);
            setStyle("-fx-background-color: transparent; -fx-padding: 2 6 2 6;");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Message renderers
    // ─────────────────────────────────────────────────────────────────────────

    private void appendSystem(String text) {
        Platform.runLater(() -> {
            Label l = new Label("— " + text + " —");
            l.setFont(Font.font("SansSerif", 11));
            l.setTextFill(Color.web(TEXT_SUB));
            HBox r = new HBox(l);
            r.setAlignment(Pos.CENTER);
            r.setPadding(new Insets(4));
            chatBox.getChildren().add(r);
            scrollBottom();
        });
    }

    private void appendText(String sender, String message) {
        Platform.runLater(() -> {
            boolean mine = sender.equals(username);
            Label msg = new Label(message);
            msg.setWrapText(true); msg.setMaxWidth(420);
            msg.setFont(Font.font("SansSerif", 13));
            msg.setTextFill(Color.web(mine ? "#1E1E2E" : TEXT));

            Label time = label(LocalTime.now().format(TIME_FMT), mine ? "#44475A" : TEXT_SUB, 10);

            VBox bubble;
            if (mine) {
                bubble = new VBox(4, msg, time);
                bubble.setStyle("-fx-background-color: " + ACCENT + "; " +
                                "-fx-background-radius: 16 2 16 16; -fx-padding: 10 14 10 14;");
            } else {
                Label who = label(sender, ACCENT, 11);
                who.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
                bubble = new VBox(3, who, msg, time);
                bubble.setStyle("-fx-background-color: " + CARD + "; " +
                                "-fx-background-radius: 2 16 16 16; -fx-padding: 10 14 10 14;");
            }
            bubble.setMaxWidth(440);

            HBox row = new HBox(bubble);
            row.setPadding(new Insets(1, 0, 1, 0));
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            chatBox.getChildren().add(row);
            scrollBottom();
        });
    }

    private void appendFile(String sender, String fileName, long fileSize,
                            boolean mine, String srcIP, int srcPort) {
        Platform.runLater(() -> {
            String ext = ext(fileName);
            Label iconL = new Label(fileIcon(ext));
            iconL.setFont(Font.font(30));

            Label nameL = new Label(fileName);
            nameL.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
            nameL.setTextFill(Color.web(TEXT));
            nameL.setWrapText(true); nameL.setMaxWidth(220);

            Label metaL = label(formatSize(fileSize) + "  ·  " + (ext.isEmpty() ? "file" : ext.toUpperCase()), TEXT_SUB, 11);
            Label timeL = label(LocalTime.now().format(TIME_FMT), TEXT_SUB, 10);

            VBox info = new VBox(4, nameL, metaL, timeL);

            // Action
            javafx.scene.Node action;
            if (mine) {
                action = label("✅ Đã gửi", GREEN, 11);
            } else {
                Button btn = new Button("⬇  Tải file");
                btn.setStyle("-fx-background-color: " + BLUE + "; -fx-text-fill: " + BG + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 12 5 12;");
                btn.setOnAction(e -> doDownload(sender, fileName, srcIP, srcPort, btn, null, false));
                action = btn;
            }

            HBox card = new HBox(12, iconL, info, action);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(12, 16, 12, 14));
            card.setMaxWidth(380);
            card.setStyle("-fx-background-color: " + (mine ? CARD_ME : CARD) + "; " +
                          "-fx-background-radius: 12; " +
                          "-fx-border-color: " + (mine ? ACCENT : BORDER) + "; " +
                          "-fx-border-width: 1; -fx-border-radius: 12;");

            HBox row = new HBox(card);
            row.setPadding(new Insets(2, 0, 2, 0));
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            chatBox.getChildren().add(row);
            scrollBottom();
        });
    }

    private void appendImage(String sender, String fileName, long fileSize,
                             byte[] thumbnail, boolean mine, String srcIP, int srcPort) {
        Platform.runLater(() -> {
            // Thumbnail preview
            ImageView iv = new ImageView();
            iv.setPreserveRatio(true);
            iv.setFitWidth(240);
            iv.setFitHeight(180);
            iv.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2);");
            if (thumbnail != null && thumbnail.length > 0) {
                try { iv.setImage(new Image(new ByteArrayInputStream(thumbnail))); }
                catch (Exception ignored) {}
            }

            Label nameL = new Label(fileName);
            nameL.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
            nameL.setTextFill(Color.web(TEXT));
            nameL.setWrapText(true); nameL.setMaxWidth(230);

            String extUp = ext(fileName).toUpperCase();
            Label metaL = label(formatSize(fileSize) + (extUp.isEmpty() ? "" : "  ·  " + extUp), TEXT_SUB, 11);
            Label timeL = label(LocalTime.now().format(TIME_FMT), TEXT_SUB, 10);

            javafx.scene.Node action;
            if (mine) {
                action = label("✅ Đã gửi (preview đã gửi, ảnh gốc sẽ được tải khi đối phương nhấn)", GREEN, 10);
                ((Label)action).setWrapText(true);
                ((Label)action).setMaxWidth(230);
            } else {
                Button btnView = new Button("⬇  Xem ảnh đầy đủ  (" + formatSize(fileSize) + ")");
                btnView.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: " + BG + "; " +
                                 "-fx-font-weight: bold; -fx-background-radius: 8; " +
                                 "-fx-cursor: hand; -fx-padding: 7 14 7 14;");
                btnView.setOnAction(e -> doDownload(sender, fileName, srcIP, srcPort, btnView, iv, true));
                action = btnView;
            }

            VBox card = new VBox(8, iv, nameL, metaL, action, timeL);
            card.setPadding(new Insets(12));
            card.setMaxWidth(268);
            card.setStyle("-fx-background-color: " + (mine ? CARD_ME : CARD) + "; " +
                          "-fx-background-radius: 12; " +
                          "-fx-border-color: " + (mine ? ACCENT : BORDER) + "; " +
                          "-fx-border-width: 1; -fx-border-radius: 12;");

            if (!mine) {
                Label who = label(sender, ACCENT, 11);
                who.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
                card.getChildren().add(0, who);
            }

            HBox row = new HBox(card);
            row.setPadding(new Insets(2, 0, 2, 0));
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            chatBox.getChildren().add(row);
            scrollBottom();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Actions (send)
    // ─────────────────────────────────────────────────────────────────────────

    private void onSendText() {
        String text = txtMessage.getText().trim();
        if (text.isEmpty() || targetIP == null) return;
        String to = targetUsername;
        txtMessage.clear();
        new Thread(() -> {
            try {
                P2PClient.sendMessage(targetIP, targetP2PPort, username, text);
                appendText(username, text);
                sendLog("LOG_MSG", dos -> { dos.writeUTF(to != null ? to : targetIP); dos.writeUTF(text); });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Lỗi gửi", "Không thể gửi tới " + targetIP));
            }
        }).start();
    }

    private void onSendFile() {
        if (targetIP == null) { showAlert("Cảnh báo", "Chọn người nhận trước!"); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn file để gửi");
        File f = fc.showOpenDialog(primaryStage);
        if (f == null) return;
        // Nếu chọn file ảnh → dùng luồng ảnh
        if (IMG_EXTS.contains(ext(f.getName()))) { doSendImage(f); return; }
        offeredFiles.put(f.getName(), f.getAbsolutePath());
        String to = targetUsername;
        new Thread(() -> {
            try {
                P2PClient.sendFileOffer(targetIP, targetP2PPort, username, f);
                appendFile(username, f.getName(), f.length(), true, null, 0);
                sendLog("LOG_FILE", dos -> { dos.writeUTF(to != null ? to : targetIP); dos.writeUTF(f.getName()); dos.writeLong(f.length()); });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Lỗi gửi", "Gửi file thất bại!"));
            }
        }).start();
    }

    private void onSendImage() {
        if (targetIP == null) { showAlert("Cảnh báo", "Chọn người nhận trước!"); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh để gửi");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Ảnh (*.jpg *.jpeg *.png *.gif *.bmp *.webp)",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.webp"));
        File f = fc.showOpenDialog(primaryStage);
        if (f != null) doSendImage(f);
    }

    private void doSendImage(File f) {
        offeredFiles.put(f.getName(), f.getAbsolutePath());
        String to = targetUsername;
        new Thread(() -> {
            try {
                byte[] thumb = generateThumbnail(f);
                P2PClient.sendImageOffer(targetIP, targetP2PPort, username, f, thumb);
                appendImage(username, f.getName(), f.length(), thumb, true, null, 0);
                sendLog("LOG_IMG", dos -> { dos.writeUTF(to != null ? to : targetIP); dos.writeUTF(f.getName()); dos.writeLong(f.length()); });
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Lỗi gửi ảnh", e.getMessage()));
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Download (file / ảnh gốc)
    // ─────────────────────────────────────────────────────────────────────────

    private void doDownload(String sender, String fileName,
                            String srcIP, int srcPort,
                            Button btn, ImageView previewIv, boolean isImage) {
        if (srcIP == null) {
            // Tìm lại IP từ danh sách
            String[] addr = userAddressMap.get(sender);
            if (addr == null) { showAlert("Lỗi", sender + " không còn online!"); return; }
            srcIP   = addr[0];
            srcPort = Integer.parseInt(addr[1]);
        }

        final String ip = srcIP;
        final int    port = srcPort;

        Platform.runLater(() -> {
            if (btn != null) { btn.setDisable(true); btn.setText("⏳ Đang tải..."); }
            downloadProgress.setVisible(true);
            downloadProgress.setProgress(0);
            lblDownloadInfo.setText("Đang kết nối tới " + sender + "...");
        });

        new Thread(() -> {
            try (Socket s = new Socket(ip, port);
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                 DataInputStream  dis = new DataInputStream(s.getInputStream())) {

                dos.writeUTF("DOWNLOAD_REQ");
                dos.writeUTF(fileName);
                dos.flush();

                String resp = dis.readUTF();
                if (!"FILE_DATA".equals(resp)) {
                    Platform.runLater(() -> { showAlert("Lỗi", "File không còn trên máy người gửi!"); resetBtn(btn); });
                    return;
                }

                String fn = dis.readUTF();
                long   sz = dis.readLong();

                File dir  = new File(System.getProperty("user.home"), "Downloads/P2PChat");
                dir.mkdirs();
                File save = new File(dir, fn);

                byte[]                buf   = new byte[8192];
                long                  total = 0;
                long                  start = System.currentTimeMillis();
                ByteArrayOutputStream imgBuf = isImage ? new ByteArrayOutputStream((int) Math.min(sz, 20_000_000)) : null;

                try (FileOutputStream fos = new FileOutputStream(save)) {
                    int r;
                    while (total < sz && (r = dis.read(buf, 0, (int) Math.min(buf.length, sz - total))) > 0) {
                        fos.write(buf, 0, r);
                        if (imgBuf != null) imgBuf.write(buf, 0, r);
                        total += r;
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed > 0) {
                            double speed = (total / 1024.0) / (elapsed / 1000.0);
                            int    eta   = (int) (((sz - total) / 1024.0) / Math.max(speed, 0.1));
                            double pct   = (double) total / sz;
                            Platform.runLater(() -> {
                                downloadProgress.setProgress(pct);
                                lblDownloadInfo.setText(String.format("%.1f KB/s  ·  ~%ds  ·  %s",
                                        speed, Math.max(0, eta), fn));
                            });
                        }
                    }
                }

                if (isImage && imgBuf != null) {
                    byte[] imgBytes = imgBuf.toByteArray();
                    Platform.runLater(() -> {
                        downloadProgress.setProgress(1.0);
                        lblDownloadInfo.setText("✅ Đã tải: " + fn + "  →  " + save.getAbsolutePath());
                        if (btn != null) btn.setText("✅ Đã tải");
                        // Cập nhật thumbnail thành ảnh đầy đủ trong bubble
                        if (previewIv != null) {
                            Image full = new Image(new ByteArrayInputStream(imgBytes));
                            previewIv.setImage(full);
                        }
                        showImageViewer(fn, imgBytes, save);
                    });
                } else {
                    Platform.runLater(() -> {
                        downloadProgress.setProgress(1.0);
                        lblDownloadInfo.setText("✅ Đã lưu: " + save.getAbsolutePath());
                        if (btn != null) btn.setText("✅ Đã tải");
                    });
                }

            } catch (Exception ex) {
                Platform.runLater(() -> { showAlert("Lỗi kết nối", ex.getMessage()); resetBtn(btn); });
            }
        }).start();
    }

    private void resetBtn(Button btn) {
        if (btn != null) { btn.setDisable(false); btn.setText("⬇ Thử lại"); }
    }

    // ── Image viewer popup ────────────────────────────────────────────────────

    private void showImageViewer(String title, byte[] imgBytes, File saved) {
        Stage win = new Stage();
        win.setTitle("🖼  " + title);
        win.initModality(Modality.NONE);

        Image img = new Image(new ByteArrayInputStream(imgBytes));
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        double maxW = Math.min(img.getWidth() > 0 ? img.getWidth() : 800, 960);
        double maxH = Math.min(img.getHeight() > 0 ? img.getHeight() : 600, 720);
        iv.setFitWidth(maxW);
        iv.setFitHeight(maxH);

        ScrollPane sp = new ScrollPane(iv);
        sp.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");
        sp.setFitToWidth(true);

        Label pathLbl = new Label("💾  " + saved.getAbsolutePath());
        pathLbl.setFont(Font.font("Consolas", 11));
        pathLbl.setTextFill(Color.web(TEXT_SUB));
        pathLbl.setPadding(new Insets(8, 14, 8, 14));
        pathLbl.setStyle("-fx-background-color: " + SURFACE + ";");

        VBox root = new VBox(sp, pathLbl);
        root.setStyle("-fx-background-color: " + BG + ";");
        VBox.setVgrow(sp, Priority.ALWAYS);

        win.setScene(new Scene(root, maxW + 40, maxH + 56));
        win.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Network
    // ─────────────────────────────────────────────────────────────────────────

    private void connectCentralServer() {
        new Thread(() -> {
            try {
                centralServerSocket = new Socket(serverIP, serverPort);
                centralServerDos    = new DataOutputStream(centralServerSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(centralServerSocket.getInputStream());

                centralServerDos.writeUTF("REGISTER");
                centralServerDos.writeUTF(username);
                centralServerDos.writeInt(myP2PPort);
                centralServerDos.flush();

                while (true) {
                    String hdr = dis.readUTF();
                    if ("UPDATE_USER_LIST".equals(hdr)) {
                        int cnt = dis.readInt();
                        List<String> list = new ArrayList<>();
                        for (int i = 0; i < cnt; i++) {
                            String u = dis.readUTF();
                            if (!u.startsWith(username + " -")) list.add(u);
                        }
                        // Cập nhật address map
                        userAddressMap.clear();
                        for (String u : list) {
                            String[] p = u.split(" - ");
                            if (p.length >= 2) userAddressMap.put(p[0].trim(), p[1].trim().split(":"));
                        }
                        Platform.runLater(() -> {
                            allUsers.setAll(list);
                            lblOnlineCount.setText("🟢  Online (" + list.size() + ")");
                        });
                    } else if ("CALL_REQ".equals(hdr)) {
                        String fromUser = dis.readUTF();
                        String fromIP = dis.readUTF();
                        int fromUdpPort = dis.readInt();
                        Platform.runLater(() -> handleIncomingCall(fromUser, fromIP, fromUdpPort));
                    } else if ("CALL_RES".equals(hdr)) {
                        String fromUser = dis.readUTF();
                        boolean isAccept = dis.readBoolean();
                        int targetUdpPort = 0;
                        if (isAccept) targetUdpPort = dis.readInt();
                        
                        final int udpPort = targetUdpPort;
                        Platform.runLater(() -> handleCallResponse(fromUser, isAccept, udpPort));
                    } else if ("END_CALL".equals(hdr)) {
                        String fromUser = dis.readUTF();
                        Platform.runLater(() -> {
                            if (currentCallWindow != null) {
                                currentCallWindow.closeWindow();
                                currentCallWindow = null;
                                appendSystem("Cuộc gọi với " + fromUser + " đã kết thúc.");
                            }
                        });
                    }
                }
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Mất kết nối", "Không thể kết nối tới Central Server!"));
            }
        }).start();
    }

    private void startP2PListener() {
        INetworkCallback cb = new INetworkCallback() {
            @Override public void onMessageReceived(String s, String m) { appendText(s, m); }

            @Override
            public void onFileOfferReceived(String sender, String fileName, long fileSize) {
                String[] addr = userAddressMap.get(sender);
                String ip = addr != null ? addr[0] : null;
                int    pt = addr != null ? Integer.parseInt(addr[1]) : 0;
                appendFile(sender, fileName, fileSize, false, ip, pt);
            }

            @Override
            public void onImageOfferReceived(String sender, String fileName, long fileSize, byte[] thumb) {
                String[] addr = userAddressMap.get(sender);
                String ip = addr != null ? addr[0] : null;
                int    pt = addr != null ? Integer.parseInt(addr[1]) : 0;
                appendImage(sender, fileName, fileSize, thumb, false, ip, pt);
            }

            @Override public void onFileDownloadProgress(String fn, long dl, long tot, double spd, int eta) {}
            @Override public void onFileSaved(String fn, String path) {}
            @Override public void onError(String msg) { Platform.runLater(() -> showAlert("Lỗi P2P", msg)); }
        };

        P2PListener listener = new P2PListener(myP2PPort, cb);
        listener.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Video Call Handlers
    // ─────────────────────────────────────────────────────────────────────────

    private void initiateVideoCall() {
        if (targetUsername == null || targetIP == null) {
            showAlert("Cảnh báo", "Hãy chọn người dùng để gọi!");
            return;
        }
        if (currentCallWindow != null) {
            showAlert("Cảnh báo", "Bạn đang trong một cuộc gọi khác!");
            return;
        }
        
        appendSystem("Đang gọi Video cho " + targetUsername + "...");
        sendLog("CALL_REQ", dos -> {
            dos.writeUTF(targetUsername);
            dos.writeInt(myUdpPort);
        });
    }

    private void handleIncomingCall(String fromUser, String fromIP, int fromUdpPort) {
        if (currentCallWindow != null) {
            // Đang bận, tự động từ chối
            sendLog("CALL_RES", dos -> {
                dos.writeUTF(fromUser);
                dos.writeBoolean(false); // Reject
            });
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cuộc gọi đến");
        alert.setHeaderText(fromUser + " đang gọi video cho bạn...");
        
        ButtonType btnAccept = new ButtonType("Chấp nhận", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnReject = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnAccept, btnReject);
        
        Optional<ButtonType> res = alert.showAndWait();
        boolean accept = res.isPresent() && res.get() == btnAccept;
        
        sendLog("CALL_RES", dos -> {
            dos.writeUTF(fromUser);
            dos.writeBoolean(accept);
            if (accept) dos.writeInt(myUdpPort);
        });
        
        if (accept) {
            appendSystem("Đã bắt đầu cuộc gọi video với " + fromUser);
            currentCallWindow = new CallWindow(fromUser, myUdpPort, fromIP, fromUdpPort, () -> {
                sendLog("END_CALL", dos -> {
                    try { dos.writeUTF(fromUser); } catch(Exception ignored) {}
                });
                appendSystem("Đã kết thúc cuộc gọi với " + fromUser);
                currentCallWindow = null;
            });
        } else {
            appendSystem("Đã từ chối cuộc gọi từ " + fromUser);
        }
    }

    private void handleCallResponse(String fromUser, boolean isAccept, int targetUdpPort) {
        if (!isAccept) {
            appendSystem(fromUser + " đã từ chối cuộc gọi video.");
            return;
        }
        
        String[] addr = userAddressMap.get(fromUser);
        if (addr == null) return;
        String targetIp = addr[0];
        
        appendSystem(fromUser + " đã chấp nhận cuộc gọi video!");
        currentCallWindow = new CallWindow(fromUser, myUdpPort, targetIp, targetUdpPort, () -> {
            sendLog("END_CALL", dos -> {
                try { dos.writeUTF(fromUser); } catch(Exception ignored) {}
            });
            appendSystem("Đã kết thúc cuộc gọi với " + fromUser);
            currentCallWindow = null;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Logging to server
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface LogWriter { void write(DataOutputStream d) throws IOException; }

    private synchronized void sendLog(String type, LogWriter w) {
        try {
            if (centralServerDos == null) return;
            centralServerDos.writeUTF(type);
            w.write(centralServerDos);
            centralServerDos.flush();
        } catch (IOException ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Thumbnail generation (AWT)
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] generateThumbnail(File imgFile) throws IOException {
        BufferedImage orig = ImageIO.read(imgFile);
        if (orig == null) throw new IOException("Không đọc được ảnh: " + imgFile.getName());

        int maxW = 320, maxH = 240;
        int w = orig.getWidth(), h = orig.getHeight();
        double scale = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));
        int tw = Math.max(1, (int) (w * scale));
        int th = Math.max(1, (int) (h * scale));

        BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(orig, 0, 0, tw, th, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(thumb, "JPEG", baos);
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Login dialog
    // ─────────────────────────────────────────────────────────────────────────

    private boolean showLoginDialog() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Đăng nhập  ·  P2P Chat");
        dlg.setHeaderText("Nhập thông tin kết nối");

        TextField tName  = styledField("User" + (int)(Math.random() * 99 + 1));
        TextField tP2P   = styledField("8001");
        TextField tSrvIP = styledField("127.0.0.1");
        TextField tSrvPt = styledField("9999");

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        g.add(new Label("Tên đăng nhập:"), 0, 0); g.add(tName,  1, 0);
        g.add(new Label("Port P2P:"),      0, 1); g.add(tP2P,   1, 1);
        g.add(new Label("Server IP:"),     0, 2); g.add(tSrvIP, 1, 2);
        g.add(new Label("Server Port:"),   0, 3); g.add(tSrvPt, 1, 3);

        Label hint = new Label("💡  Mỗi client cần một Port P2P khác nhau (ví dụ 8001, 8002, 8003...)");
        hint.setFont(Font.font("SansSerif", 11));
        hint.setTextFill(Color.web("#888"));
        hint.setWrapText(true);

        VBox content = new VBox(12, g, hint);
        content.setPrefWidth(340);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> r = dlg.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            username  = tName.getText().trim();
            myP2PPort = Integer.parseInt(tP2P.getText().trim());
            myUdpPort = myP2PPort + 1000; // Dùng cho Video Call
            serverIP  = tSrvIP.getText().trim();
            serverPort = Integer.parseInt(tSrvPt.getText().trim());
            return !username.isEmpty();
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private void scrollBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private String formatSize(long sz) {
        if (sz < 1024L)            return sz + " B";
        if (sz < 1024L * 1024)     return String.format("%.1f KB", sz / 1024.0);
        if (sz < 1024L << 20)      return String.format("%.2f MB", sz / (1024.0 * 1024));
        return String.format("%.2f GB", sz / (1024.0 * 1024 * 1024));
    }

    private String ext(String name) {
        int i = name.lastIndexOf('.'); return (i >= 0 && i < name.length()-1) ? name.substring(i+1).toLowerCase() : "";
    }

    private String fileIcon(String ext) {
        return switch (ext) {
            case "pdf"              -> "📕";
            case "doc","docx"       -> "📝";
            case "xls","xlsx"       -> "📊";
            case "ppt","pptx"       -> "📑";
            case "zip","rar","7z"   -> "🗜";
            case "mp3","wav","flac" -> "🎵";
            case "mp4","mkv","avi"  -> "🎬";
            case "exe","msi"        -> "⚙";
            case "txt","md"         -> "📄";
            default                 -> "📦";
        };
    }

    private static final String[] AVATAR_COLORS = {
            "#CBA6F7","#F38BA8","#FAB387","#F9E2AF","#A6E3A1","#89DCEB","#89B4FA","#B4BEFE"
    };
    private String avatarColor(String name) {
        return AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length];
    }
    private String initial(String name) {
        return name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
    }

    // ── Mini builder helpers ──────────────────────────────────────────────────

    private Label label(String text, String hex, double size) {
        Label l = new Label(text);
        l.setFont(Font.font("SansSerif", size));
        l.setTextFill(Color.web(hex));
        return l;
    }

    private Button roundButton(String text, String bg, String fg, double size) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-font-weight: bold; -fx-font-size: 14px; " +
                   "-fx-background-radius: 50; -fx-min-width: " + size + "; " +
                   "-fx-min-height: " + size + "; -fx-cursor: hand;");
        return b;
    }

    private Button iconBtn(String emoji, String tooltip) {
        Button b = new Button(emoji);
        b.setTooltip(new Tooltip(tooltip));
        b.setStyle("-fx-background-color: " + CARD + "; -fx-font-size: 17px; " +
                   "-fx-background-radius: 50; -fx-min-width: 40; -fx-min-height: 40; -fx-cursor: hand;");
        return b;
    }

    private String fieldStyle() {
        return "-fx-background-color: #252535; -fx-text-fill: " + TEXT + "; " +
               "-fx-prompt-text-fill: " + TEXT_SUB + "; -fx-border-color: " + BORDER + "; " +
               "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 10 8 10;";
    }

    private TextField styledField(String def) {
        TextField tf = new TextField(def);
        tf.setPrefWidth(200);
        return tf;
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
            a.showAndWait();
        });
    }
}