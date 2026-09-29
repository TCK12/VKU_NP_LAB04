package org.example.lab04.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
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
 * Hiển thị: trạng thái server, danh sách user online, nhật ký hoạt động.
 */
public class ServerApp extends Application implements IServerEventListener {

    // ── Toolbar ──────────────────────────────────────────────────────────────
    private TextField txtPort;
    private Button btnStart;
    private Button btnStop;
    private Label lblServerStatus;
    private Label lblServerIP;

    // ── User List ─────────────────────────────────────────────────────────────
    private ListView<String> userListView;
    private Label lblOnlineCount;

    // ── Activity Log ──────────────────────────────────────────────────────────
    private TextArea logArea;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        launch(args);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  JavaFX lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void start(Stage primaryStage) {
        CentralServer.eventListener = this;

        primaryStage.setTitle("🖥  Central Server – Bảng Điều Khiển");
        primaryStage.setMinWidth(950);
        primaryStage.setMinHeight(600);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1E1E2E;");

        root.setTop(buildToolbar());
        root.setLeft(buildUserPanel());
        root.setCenter(buildLogPanel());

        Scene scene = new Scene(root, 1050, 650);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            CentralServer.stop();
            Platform.exit();
        });
        primaryStage.show();

        appendLog("ℹ️  Chào mừng! Nhấn ▶ Khởi Động Server để bắt đầu.", "#7EC8E3");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI Builder
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setPadding(new Insets(14, 20, 14, 20));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #13131F; -fx-border-color: #3A3A5C; -fx-border-width: 0 0 2 0;");

        // Logo / title
        Label lblTitle = new Label("⚡ Central Server");
        lblTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
        lblTitle.setTextFill(Color.web("#CBA6F7"));

        // Separator
        Region sep1 = new Region();
        sep1.setPrefWidth(20);

        // Port input
        Label lblPortLabel = new Label("Port:");
        lblPortLabel.setTextFill(Color.web("#CDD6F4"));
        lblPortLabel.setFont(Font.font("SansSerif", 13));

        txtPort = new TextField(String.valueOf(CentralServer.DEFAULT_PORT));
        txtPort.setPrefWidth(75);
        txtPort.setStyle("-fx-background-color: #2A2A3E; -fx-text-fill: #CDD6F4; "
                + "-fx-border-color: #585B70; -fx-border-radius: 6; -fx-background-radius: 6;");

        // Buttons
        btnStart = new Button("▶  Khởi Động");
        btnStart.setStyle(buttonStyle("#A6E3A1", "#1E1E2E"));
        btnStart.setOnAction(e -> startServer());

        btnStop = new Button("⏹  Dừng");
        btnStop.setStyle(buttonStyle("#F38BA8", "#1E1E2E"));
        btnStop.setDisable(true);
        btnStop.setOnAction(e -> stopServer());

        // Status labels
        lblServerStatus = new Label("● Chưa khởi động");
        lblServerStatus.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblServerStatus.setTextFill(Color.web("#F38BA8"));

        lblServerIP = new Label("");
        lblServerIP.setFont(Font.font("SansSerif", 12));
        lblServerIP.setTextFill(Color.web("#A6ADC8"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                lblTitle, sep1,
                lblPortLabel, txtPort,
                btnStart, btnStop,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                lblServerStatus, lblServerIP,
                spacer
        );
        return toolbar;
    }

    private VBox buildUserPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(260);
        panel.setPadding(new Insets(14, 10, 14, 14));
        panel.setStyle("-fx-background-color: #181825; -fx-border-color: #3A3A5C; -fx-border-width: 0 2 0 0;");

        lblOnlineCount = new Label("🟢 Người dùng Online (0)");
        lblOnlineCount.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        lblOnlineCount.setTextFill(Color.web("#A6E3A1"));

        userListView = new ListView<>();
        userListView.setStyle(
                "-fx-background-color: #1E1E2E; "
                + "-fx-control-inner-background: #1E1E2E; "
                + "-fx-border-color: #585B70; "
                + "-fx-border-radius: 8; "
                + "-fx-background-radius: 8;");
        VBox.setVgrow(userListView, Priority.ALWAYS);

        // Custom cell để hiển thị đẹp hơn
        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    // Phân tích "username - IP:port"
                    String display = item;
                    String[] parts = item.split(" - ");
                    if (parts.length >= 2) {
                        display = "👤 " + parts[0] + "\n    🌐 " + parts[1];
                    }
                    setText(display);
                    setStyle("-fx-background-color: #252535; -fx-text-fill: #CDD6F4; "
                            + "-fx-font-size: 12px; -fx-padding: 8px;");
                }
            }
        });

        // Thống kê nhanh
        Label lblStatTitle = new Label("📊 Thống kê phiên");
        lblStatTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        lblStatTitle.setTextFill(Color.web("#89DCEB"));
        lblStatTitle.setPadding(new Insets(8, 0, 0, 0));

        panel.getChildren().addAll(lblOnlineCount, userListView, lblStatTitle);
        return panel;
    }

    private VBox buildLogPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(14, 16, 14, 16));
        panel.setStyle("-fx-background-color: #1E1E2E;");

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label lblLogTitle = new Label("📋 Nhật Ký Hoạt Động");
        lblLogTitle.setFont(Font.font("SansSerif", FontWeight.BOLD, 15));
        lblLogTitle.setTextFill(Color.web("#CBA6F7"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClear = new Button("🗑  Xóa log");
        btnClear.setStyle(buttonStyle("#F9E2AF", "#1E1E2E"));
        btnClear.setOnAction(e -> logArea.clear());

        header.getChildren().addAll(lblLogTitle, spacer, btnClear);

        // Log text area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle(
                "-fx-control-inner-background: #11111B; "
                + "-fx-text-fill: #CDD6F4; "
                + "-fx-font-family: 'Consolas', 'Courier New', monospace; "
                + "-fx-font-size: 13px; "
                + "-fx-border-color: #585B70; "
                + "-fx-border-radius: 8; "
                + "-fx-background-radius: 8;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        panel.getChildren().addAll(header, logArea);
        return panel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Server control
    // ─────────────────────────────────────────────────────────────────────────

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showAlert("Port không hợp lệ", "Vui lòng nhập port trong khoảng 1 – 65535.");
            return;
        }

        try {
            CentralServer.startAsync(port);
            // UI cập nhật sẽ được gọi qua onServerStarted callback
        } catch (IOException ex) {
            appendLog("❌ Không thể khởi động server: " + ex.getMessage(), "#F38BA8");
            showAlert("Lỗi khởi động", ex.getMessage());
        }
    }

    private void stopServer() {
        CentralServer.stop();
        // onServerStopped sẽ được gọi
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  IServerEventListener
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onServerStarted(int port) {
        Platform.runLater(() -> {
            btnStart.setDisable(true);
            btnStop.setDisable(false);
            txtPort.setDisable(true);
            lblServerStatus.setText("● Đang chạy – Port " + port);
            lblServerStatus.setTextFill(Color.web("#A6E3A1"));

            // Lấy IPv4 của Tailscale
            String tailscaleIP = getTailscaleIPv4();
            lblServerIP.setText("IP (Tailscale): " + tailscaleIP);

            appendLog("✅ Server đã khởi động thành công trên port " + port, "#A6E3A1");
        });
    }

    // Hàm bổ trợ để lọc đúng IPv4 của Tailscale
    private String getTailscaleIPv4() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();

                // Bỏ qua card bị tắt
                if (!ni.isUp()) continue;

                String name = ni.getDisplayName().toLowerCase();

                // Kiểm tra tên adapter chứa "tailscale"
                if (name.contains("tailscale")) {
                    java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress addr = addresses.nextElement();
                        // Lấy địa chỉ IPv4
                        if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Nếu Tailscale chưa bật hoặc không tìm thấy, lấy tạm IP mặc định
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    @Override
    public void onServerStopped() {
        Platform.runLater(() -> {
            btnStart.setDisable(false);
            btnStop.setDisable(true);
            txtPort.setDisable(false);
            lblServerStatus.setText("● Đã dừng");
            lblServerStatus.setTextFill(Color.web("#F38BA8"));
            lblServerIP.setText("");
            userListView.getItems().clear();
            lblOnlineCount.setText("🟢 Người dùng Online (0)");
            appendLog("🛑 Server đã dừng.", "#F38BA8");
        });
    }

    @Override
    public void onClientConnected(String userInfo) {
        Platform.runLater(() -> {
            // userInfo dạng: "username - IP:port"
            String username = userInfo.split(" - ")[0];
            appendLog("🟢 [KẾT NỐI]  " + username + "  đã đăng nhập\n"
                    + "              📍 Địa chỉ: " + userInfo.split(" - ")[1], "#A6E3A1");
        });
    }

    @Override
    public void onClientDisconnected(String userInfo) {
        Platform.runLater(() -> {
            String username = userInfo.split(" - ")[0];
            appendLog("🔴 [NGẮT KẾT NỐI]  " + username + " đã offline.", "#FAB387");
        });
    }

    @Override
    public void onMessageSent(String fromUser, String toUser, String message) {
        Platform.runLater(() ->
            appendLog("💬 [TIN NHẮN]  " + fromUser + "  ➜  " + toUser
                    + "\n              📝 Nội dung: \"" + message + "\"", "#89B4FA")
        );
    }

    @Override
    public void onFileSent(String fromUser, String toUser, String fileName, long fileSize) {
        Platform.runLater(() -> {
            String ext = getExtension(fileName);
            String sizeStr = formatSize(fileSize);
            appendLog("📁 [GỬI FILE]  " + fromUser + "  ➜  " + toUser
                    + "\n              📄 Tên file: " + fileName
                    + "\n              🗂️  Định dạng: " + (ext.isEmpty() ? "(không rõ)" : ext.toUpperCase())
                    + "\n              💾 Dung lượng: " + sizeStr, "#F9E2AF");
        });
    }

    @Override
    public void onUserListUpdated(List<String> activeUsers) {
        Platform.runLater(() -> {
            userListView.getItems().setAll(activeUsers);
            lblOnlineCount.setText("🟢 Người dùng Online (" + activeUsers.size() + ")");
        });
    }

    @Override
    public void onLog(String message) {
        Platform.runLater(() -> appendLog(message, "#CDD6F4"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void appendLog(String text, String colorHex) {
        String time = LocalTime.now().format(TIME_FMT);
        String line = "[" + time + "]  " + text + "\n";
        logArea.appendText(line);
    }

    private String formatSize(long size) {
        if (size < 1024L)
            return size + " B";
        if (size < 1024L * 1024)
            return String.format("%.2f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024)
            return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0 && dot < fileName.length() - 1)
                ? fileName.substring(dot + 1).toLowerCase()
                : "";
    }

    private String buttonStyle(String bgColor, String fgColor) {
        return "-fx-background-color: " + bgColor + "; "
                + "-fx-text-fill: " + fgColor + "; "
                + "-fx-font-weight: bold; "
                + "-fx-font-size: 12px; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 7 16 7 16; "
                + "-fx-cursor: hand;";
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
