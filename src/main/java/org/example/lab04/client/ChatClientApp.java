package org.example.lab04.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.lab04.network.INetworkCallback;
import org.example.lab04.network.P2PClient;
import org.example.lab04.network.P2PListener;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChatClientApp extends Application {

    private ListView<String> userListView = new ListView<>();
    private VBox chatBox = new VBox(10);
    private ScrollPane scrollPane = new ScrollPane();
    private TextField txtMessage = new TextField();
    private Button btnSend = new Button("Gửi Chat");
    private Button btnSendFile = new Button("📁 Gửi File");
    private Label lblStatus = new Label("Trạng thái: Đang chờ chọn user...");

    private String username;
    private int myP2PPort;
    private String serverIP = "127.0.0.1";
    private int serverPort = 9999;

    private String targetIP = null;
    private int targetP2PPort = 0;
    private Socket centralServerSocket;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        if (!showLoginDialog()) {
            System.exit(0);
            return;
        }

        setupUI(primaryStage);
        startP2PListener();
        connectAndListenCentralServer();
    }

    private void connectAndListenCentralServer() {
        new Thread(() -> {
            try {
                centralServerSocket = new Socket(serverIP, serverPort);
                DataOutputStream dos = new DataOutputStream(centralServerSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(centralServerSocket.getInputStream());

                dos.writeUTF("REGISTER");
                dos.writeUTF(username);
                dos.writeInt(myP2PPort);
                dos.flush();

                while (true) {
                    String header = dis.readUTF();
                    if ("UPDATE_USER_LIST".equals(header)) {
                        int count = dis.readInt();
                        List<String> onlineUsers = new ArrayList<>();

                        for (int i = 0; i < count; i++) {
                            String user = dis.readUTF();
                            if (!user.startsWith(username + " -")) {
                                onlineUsers.add(user);
                            }
                        }

                        Platform.runLater(() -> {
                            userListView.getItems().clear();
                            userListView.getItems().addAll(onlineUsers);
                            if (onlineUsers.isEmpty()) {
                                lblStatus.setText("Chưa có user nào khác online.");
                            } else {
                                lblStatus.setText("Danh sách user online đã được cập nhật.");
                            }
                        });
                    }
                }
            } catch (IOException e) {
                Platform.runLater(() -> lblStatus.setText("❌ Mất kết nối tới Central Server!"));
            }
        }).start();
    }

    private void startP2PListener() {
        INetworkCallback uiCallback = new INetworkCallback() {
            @Override
            public void onMessageReceived(String sender, String message) {
                appendMessage(sender, message, false);
            }

            @Override
            public void onFileReceived(String sender, String fileName) {
                appendMessage(sender, "📁 [Đã nhận File]: " + fileName + " (Lưu tại /downloads)", true);
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> showAlert("Lỗi P2P", errorMessage));
            }
        };

        P2PListener p2pServer = new P2PListener(myP2PPort, uiCallback);
        p2pServer.start();
    }

    private void sendTextMessage() {
        String text = txtMessage.getText().trim();
        if (text.isEmpty() || targetIP == null) return;

        new Thread(() -> {
            try {
                P2PClient.sendMessage(targetIP, targetP2PPort, username, text);
                appendMessage("Tôi", text, false);
                Platform.runLater(() -> txtMessage.clear());
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Lỗi P2P", "Không thể gửi tới " + targetIP));
            }
        }).start();
    }

    private void openFileChooserAndSend(Stage stage) {
        if (targetIP == null) {
            showAlert("Cảnh báo", "Vui lòng chọn người nhận!");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            new Thread(() -> {
                try {
                    P2PClient.sendFile(targetIP, targetP2PPort, username, selectedFile);
                    appendMessage("Tôi", "📁 [Đã gửi File]: " + selectedFile.getName(), true);
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert("Lỗi P2P", "Truyền file thất bại!"));
                }
            }).start();
        }
    }

    private void setupUI(Stage primaryStage) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        VBox leftSidebar = new VBox(8, new Label("🟢 Danh sách User Online"), userListView);
        leftSidebar.setPrefWidth(220);
        VBox.setVgrow(userListView, Priority.ALWAYS);

        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String[] parts = newVal.split(" - ")[1].split(":");
                targetIP = parts[0];
                targetP2PPort = Integer.parseInt(parts[1]);
                lblStatus.setText("Đang chat trực tiếp với: " + newVal);
            }
        });

        scrollPane.setContent(chatBox);
        scrollPane.setFitToWidth(true);

        HBox inputBar = new HBox(8, txtMessage, btnSend, btnSendFile);
        HBox.setHgrow(txtMessage, Priority.ALWAYS);
        inputBar.setAlignment(Pos.CENTER);

        btnSend.setOnAction(e -> sendTextMessage());
        txtMessage.setOnAction(e -> sendTextMessage());
        btnSendFile.setOnAction(e -> openFileChooserAndSend(primaryStage));

        mainLayout.setLeft(leftSidebar);
        BorderPane.setMargin(leftSidebar, new Insets(0, 10, 0, 0));
        mainLayout.setCenter(new VBox(10, scrollPane, inputBar, lblStatus));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        primaryStage.setScene(new Scene(mainLayout, 800, 500));
        primaryStage.setTitle("P2P Chat - " + username + " (Port: " + myP2PPort + ")");
        primaryStage.setOnCloseRequest(e -> {
            try {
                if (centralServerSocket != null && !centralServerSocket.isClosed()) {
                    centralServerSocket.close();
                }
            } catch (IOException ignored) {}
            System.exit(0);
        });
        primaryStage.show();
    }

    private boolean showLoginDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Đăng nhập Client");

        TextField txtName = new TextField("User" + (int)(Math.random() * 100));
        TextField txtP2PPort = new TextField("8001");
        TextField txtServerIP = new TextField("127.0.0.1");
        TextField txtServerPort = new TextField("9999");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Tên:"), 0, 0); grid.add(txtName, 1, 0);
        grid.add(new Label("Port P2P:"), 0, 1); grid.add(txtP2PPort, 1, 1);
        grid.add(new Label("Server IP:"), 0, 2); grid.add(txtServerIP, 1, 2);
        grid.add(new Label("Server Port:"), 0, 3); grid.add(txtServerPort, 1, 3);

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

    private void appendMessage(String sender, String msg, boolean isFile) {
        Platform.runLater(() -> {
            Label lbl = new Label(sender + ": " + msg);
            lbl.setWrapText(true);
            lbl.setMaxWidth(400);

            HBox row = new HBox(lbl);
            if (sender.equals("Tôi")) {
                row.setAlignment(Pos.CENTER_RIGHT);
                lbl.setStyle("-fx-background-color: #DCF8C6; -fx-padding: 8px; -fx-background-radius: 8px;");
            } else {
                row.setAlignment(Pos.CENTER_LEFT);
                lbl.setStyle("-fx-background-color: #EAEAEA; -fx-padding: 8px; -fx-background-radius: 8px;");
            }

            if (isFile) lbl.setStyle(lbl.getStyle() + "-fx-font-weight: bold; -fx-text-fill: #0066CC;");

            chatBox.getChildren().add(row);
            scrollPane.setVvalue(1.0);
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}