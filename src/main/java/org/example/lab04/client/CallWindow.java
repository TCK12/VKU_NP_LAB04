package org.example.lab04.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class CallWindow {
    
    private Stage stage;
    private VideoCallService callService;
    private String targetUsername;
    private Runnable onEndCall;

    public CallWindow(String targetUsername, int localUdpPort, String targetIP, int targetPort, Runnable onEndCall) {
        this.targetUsername = targetUsername;
        this.onEndCall = onEndCall;
        
        stage = new Stage();
        stage.setTitle("Video Call: " + targetUsername);
        
        ImageView remoteView = new ImageView();
        remoteView.setFitWidth(640);
        remoteView.setFitHeight(480);
        remoteView.setPreserveRatio(true);
        
        ImageView localView = new ImageView();
        localView.setFitWidth(160);
        localView.setFitHeight(120);
        localView.setPreserveRatio(true);
        
        callService = new VideoCallService(remoteView, localView);
        
        // Layout
        StackPane videoPane = new StackPane();
        videoPane.setStyle("-fx-background-color: #000;");
        videoPane.getChildren().add(remoteView);
        
        StackPane localPane = new StackPane(localView);
        localPane.setStyle("-fx-border-color: #CBA6F7; -fx-border-width: 2;");
        StackPane.setAlignment(localPane, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localPane, new Insets(10));
        videoPane.getChildren().add(localPane);
        
        // Controls
        Button btnMic = createBtn("🎤 Tắt Mic");
        btnMic.setOnAction(e -> {
            boolean isMuted = !callService.isMicMuted();
            callService.setMicMuted(isMuted);
            btnMic.setText(isMuted ? "🎤 Bật Mic" : "🎤 Tắt Mic");
            btnMic.setStyle(isMuted ? "-fx-background-color: #F38BA8; -fx-text-fill: #1E1E2E;" : "-fx-background-color: #313244; -fx-text-fill: #CDD6F4;");
        });
        
        Button btnCam = createBtn("📷 Tắt Cam");
        btnCam.setOnAction(e -> {
            boolean isOff = !callService.isCamOff();
            callService.setCamOff(isOff);
            btnCam.setText(isOff ? "📷 Bật Cam" : "📷 Tắt Cam");
            btnCam.setStyle(isOff ? "-fx-background-color: #F38BA8; -fx-text-fill: #1E1E2E;" : "-fx-background-color: #313244; -fx-text-fill: #CDD6F4;");
        });
        
        Button btnEnd = createBtn("❌ Kết Thúc");
        btnEnd.setStyle("-fx-background-color: #F38BA8; -fx-text-fill: #1E1E2E; -fx-font-weight: bold;");
        btnEnd.setOnAction(e -> endCall());
        
        HBox controls = new HBox(15, btnMic, btnCam, btnEnd);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(15));
        controls.setStyle("-fx-background-color: #1E1E2E;");
        
        BorderPane root = new BorderPane();
        root.setCenter(videoPane);
        root.setBottom(controls);
        
        stage.setScene(new Scene(root, 640, 560));
        stage.setOnCloseRequest(e -> endCall());
        
        try {
            callService.start(localUdpPort, targetIP, targetPort);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            endCall();
        }
    }
    
    private Button createBtn(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #313244; -fx-text-fill: #CDD6F4; -fx-font-size: 14px; -fx-padding: 8 16 8 16;");
        return b;
    }
    
    public void closeWindow() {
        if (callService != null) callService.stop();
        Platform.runLater(() -> stage.close());
    }
    
    private void endCall() {
        closeWindow();
        if (onEndCall != null) onEndCall.run();
    }
}
