package org.example.lab04.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Giao diện JavaFX cho Central Directory Server.
 * Dark theme – hiển thị user online, nhật ký chat, file và ảnh.
 */
public class ServerApp extends Application implements IServerEventListener {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final String BG      = "#1E1E2E";
    private static final String SURFACE = "#181825";
    private static final String CARD    = "#313244";
    private static final String TEXT    = "#CDD6F4";
    private static final String TEXT_S  = "#A6ADC8";
    private static final String ACCENT  = "#CBA6F7";
    private static final String GREEN   = "#A6E3A1";
    private static final String BLUE    = "#89B4FA";
    private static final String YELLOW  = "#F9E2AF";
    private static final String RED     = "#F38BA8";
    private static final String ORANGE  = "#FAB387";
    private static final String BORDER  = "#3A3A5C";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── UI fields ─────────────────────────────────────────────────────────────
    private TextField    txtPort;
    private Button       btnStart, btnStop;
    private Label        lblStatus, lblIP;
    private ListView<String> userListView;
    private Label        lblOnlineCount;
    private TextArea     logArea;

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        CentralServer.eventListener = this;
        stage.setTitle("⚡  Central Server  –  Bảng Điều Khiển");
        stage.setMinWidth(980);
        stage.setMinHeight(580);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setTop(buildToolbar());
        root.setLeft(buildUserPanel());
        root.setCenter(buildLogPanel());

        stage.setScene(new Scene(root, 1100, 680));
        stage.setOnCloseRequest(e -> { CentralServer.stop(); Platform.exit(); });
        stage.show();

        appendLog("ℹ️  Nhấn ▶ Khởi Động để mở server.", BLUE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI builders
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(14, 22, 14, 22));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: " + SURFACE + "; " +
                     "-fx-border-color: " + BORDER + "; -fx-border-width: 0 0 2 0;");

        // Logo
        Label logo = new Label("⚡  Central Server");
        logo.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
        logo.setTextFill(Color.web(ACCENT));

        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep.setPadding(new Insets(0, 6, 0, 6));

        // Port
        Label lPort = mk("Port:", TEXT, 13);
        txtPort = new TextField(String.valueOf(CentralServer.DEFAULT_PORT));
        txtPort.setPrefWidth(78);
        txtPort.setStyle("-fx-background-color: " + CARD + "; -fx-text-fill: " + TEXT + "; " +
                         "-fx-border-color: #585B70; -fx-border-radius: 6; -fx-background-radius: 6;");

        // Buttons
        btnStart = actionBtn("▶  Khởi Động", GREEN, BG);
        btnStop  = actionBtn("⏹  Dừng",      RED,   BG);
        btnStop.setDisable(true);
        btnStart.setOnAction(e -> doStart());
        btnStop.setOnAction(e  -> CentralServer.stop());

        Separator sep2 = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep2.setPadding(new Insets(0, 8, 0, 8));

        // Status
        Circle statusDot = new Circle(6, Color.web(RED));
        lblStatus = mk("● Chưa khởi động", RED, 13);
        lblStatus.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblIP = mk("", TEXT_S, 12);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(logo, sep, lPort, txtPort, btnStart, btnStop, sep2,
                                 statusDot, lblStatus, lblIP, spacer);
        return bar;
    }

    private VBox buildUserPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(270);
        panel.setPadding(new Insets(14, 10, 14, 14));
        panel.setStyle("-fx-background-color: " + SURFACE + "; " +
                       "-fx-border-color: " + BORDER + "; -fx-border-width: 0 2 0 0;");

        lblOnlineCount = new Label("🟢  Online (0)");
        lblOnlineCount.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblOnlineCount.setTextFill(Color.web(GREEN));

        userListView = new ListView<>();
        userListView.setStyle("-fx-background-color: " + BG + "; " +
                              "-fx-control-inner-background: " + BG + "; " +
                              "-fx-border-color: #585B70; -fx-border-radius: 8;");
        VBox.setVgrow(userListView, Priority.ALWAYS);
        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setStyle("-fx-background-color: transparent;"); return; }
                String[] p = item.split(" - ");
                String name = p[0].trim(), addr = p.length > 1 ? p[1].trim() : "";

                // Avatar
                String[] colors = {"#CBA6F7","#F38BA8","#FAB387","#A6E3A1","#89B4FA"};
                String col = colors[Math.abs(name.hashCode()) % colors.length];
                Circle av = new Circle(18, Color.web(col));
                Label  lt = new Label(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
                lt.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
                lt.setTextFill(Color.WHITE);
                StackPane avP = new StackPane(av, lt);
                avP.setMinWidth(40); avP.setPrefWidth(40);

                Label ln = new Label(name);
                ln.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
                ln.setTextFill(Color.web(TEXT));
                Label la = new Label("🌐 " + addr);
                la.setFont(Font.font("Consolas", 11));
                la.setTextFill(Color.web(TEXT_S));

                HBox row = new HBox(10, avP, new VBox(3, ln, la));
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8));
                row.setStyle("-fx-background-color: #252535; -fx-background-radius: 10;");

                setGraphic(row);
                setStyle("-fx-background-color: transparent; -fx-padding: 2 4 2 4;");
            }
        });

        // Legend
        Label legendTitle = mk("📊  Nhật ký sự kiện theo màu:", TEXT_S, 11);
        legendTitle.setPadding(new Insets(8, 0, 2, 0));
        Label l1 = mk("🟢 Đăng nhập  🔴 Offline", GREEN, 11);
        Label l2 = mk("💬 Tin nhắn  📁 File  🖼 Ảnh", BLUE, 11);

        panel.getChildren().addAll(lblOnlineCount, userListView, legendTitle, l1, l2);
        return panel;
    }

    private VBox buildLogPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(14, 16, 14, 16));
        panel.setStyle("-fx-background-color: " + BG + ";");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📋  Nhật Ký Hoạt Động");
        title.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        title.setTextFill(Color.web(ACCENT));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClear = actionBtn("🗑  Xóa log", YELLOW, BG);
        btnClear.setOnAction(e -> logArea.clear());

        header.getChildren().addAll(title, spacer, btnClear);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-control-inner-background: #11111B; -fx-text-fill: " + TEXT + "; " +
                         "-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 13px; " +
                         "-fx-border-color: #585B70; -fx-border-radius: 8; -fx-background-radius: 8;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        panel.getChildren().addAll(header, logArea);
        return panel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Server control
    // ─────────────────────────────────────────────────────────────────────────

    private void doStart() {
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showAlert("Port không hợp lệ", "Nhập port từ 1 đến 65535."); return;
        }
        try {
            CentralServer.startAsync(port);
        } catch (IOException ex) {
            appendLog("❌ Không thể khởi động: " + ex.getMessage(), RED);
            showAlert("Lỗi", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  IServerEventListener
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onServerStarted(int port) {
        Platform.runLater(() -> {
            btnStart.setDisable(true); btnStop.setDisable(false); txtPort.setDisable(true);
            lblStatus.setText("● Đang chạy – Port " + port);
            lblStatus.setTextFill(Color.web(GREEN));
            try { lblIP.setText("  IP: " + InetAddress.getLocalHost().getHostAddress()); } catch (Exception ignored) {}
            appendLog("✅  Server khởi động thành công trên port " + port, GREEN);
        });
    }

    @Override
    public void onServerStopped() {
        Platform.runLater(() -> {
            btnStart.setDisable(false); btnStop.setDisable(true); txtPort.setDisable(false);
            lblStatus.setText("● Đã dừng");
            lblStatus.setTextFill(Color.web(RED));
            lblIP.setText("");
            userListView.getItems().clear();
            lblOnlineCount.setText("🟢  Online (0)");
            appendLog("🛑  Server đã dừng.", RED);
        });
    }

    @Override
    public void onClientConnected(String userInfo) {
        Platform.runLater(() -> {
            String[] p = userInfo.split(" - ");
            appendLog("🟢  [KẾT NỐI]  " + p[0] + " đã đăng nhập\n" +
                      "             📍 " + (p.length > 1 ? p[1] : ""), GREEN);
        });
    }

    @Override
    public void onClientDisconnected(String userInfo) {
        Platform.runLater(() ->
            appendLog("🔴  [OFFLINE]  " + userInfo.split(" - ")[0] + " đã ngắt kết nối.", ORANGE)
        );
    }

    @Override
    public void onMessageSent(String from, String to, String message) {
        Platform.runLater(() ->
            appendLog("💬  [TIN NHẮN]  " + from + "  ➜  " + to +
                      "\n             📝 \"" + message + "\"", BLUE)
        );
    }

    @Override
    public void onFileSent(String from, String to, String fileName, long fileSize) {
        Platform.runLater(() -> {
            String ext = ext(fileName);
            appendLog("📁  [GỬI FILE]  " + from + "  ➜  " + to +
                      "\n             📄 Tên: " + fileName +
                      "\n             🗂  Định dạng: " + (ext.isEmpty() ? "(không rõ)" : ext.toUpperCase()) +
                      "\n             💾 Dung lượng: " + formatSize(fileSize), YELLOW);
        });
    }

    @Override
    public void onImageSent(String from, String to, String fileName, long fileSize) {
        Platform.runLater(() -> {
            String ext = ext(fileName);
            appendLog("🖼  [GỬI ẢNH]  " + from + "  ➜  " + to +
                      "\n             📄 Tên: " + fileName +
                      "\n             🗂  Định dạng: " + (ext.isEmpty() ? "(không rõ)" : ext.toUpperCase()) +
                      "\n             💾 Dung lượng: " + formatSize(fileSize) +
                      "\n             📡 Đã gửi thumbnail (tiết kiệm băng thông)", ACCENT);
        });
    }

    @Override
    public void onUserListUpdated(List<String> activeUsers) {
        Platform.runLater(() -> {
            userListView.getItems().setAll(activeUsers);
            lblOnlineCount.setText("🟢  Online (" + activeUsers.size() + ")");
        });
    }

    @Override
    public void onLog(String message) {
        Platform.runLater(() -> appendLog(message, TEXT));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void appendLog(String text, String color) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "]  " + text + "\n";
        logArea.appendText(line);
    }

    private String formatSize(long sz) {
        if (sz < 1024L)        return sz + " B";
        if (sz < 1_048_576L)   return String.format("%.2f KB", sz / 1024.0);
        if (sz < 1_073_741_824L) return String.format("%.2f MB", sz / 1_048_576.0);
        return String.format("%.2f GB", sz / 1_073_741_824.0);
    }

    private String ext(String name) {
        int i = name.lastIndexOf('.'); return i >= 0 ? name.substring(i+1).toLowerCase() : "";
    }

    private Label mk(String text, String hex, double size) {
        Label l = new Label(text);
        l.setFont(Font.font("SansSerif", size));
        l.setTextFill(Color.web(hex));
        return l;
    }

    private Button actionBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
                   "-fx-font-weight: bold; -fx-font-size: 12px; " +
                   "-fx-background-radius: 8; -fx-padding: 7 16 7 16; -fx-cursor: hand;");
        return b;
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(content);
        a.showAndWait();
    }
}
