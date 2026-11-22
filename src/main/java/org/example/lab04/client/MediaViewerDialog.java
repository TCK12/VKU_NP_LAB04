package org.example.lab04.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;

public class MediaViewerDialog {

    public static void showImageViewer(Stage owner, Image image, String title) {
        if (image == null) return;

        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.initOwner(owner);
        stage.setTitle(title != null ? title : "Xem hình ảnh");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #11111B;");

        ImageView iv = new ImageView(image);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.fitWidthProperty().bind(root.widthProperty().subtract(40));
        iv.fitHeightProperty().bind(root.heightProperty().subtract(80));

        StackPane centerPane = new StackPane(iv);
        centerPane.setPadding(new Insets(10));
        root.setCenter(centerPane);

        HBox bottomBar = new HBox(12);
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setPadding(new Insets(12));
        bottomBar.setStyle("-fx-background-color: #181825;");

        Button btnClose = new Button("Đóng");
        btnClose.setStyle("-fx-background-color: #313244; -fx-text-fill: #CDD6F4; -fx-font-weight: bold; -fx-padding: 6 20 6 20; -fx-background-radius: 8; -fx-cursor: hand;");
        btnClose.setOnAction(e -> stage.close());

        bottomBar.getChildren().add(btnClose);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 750, 550);
        stage.setScene(scene);
        stage.show();
    }

    public static void showVideoPlayer(Stage owner, File videoFile) {
        if (videoFile == null || !videoFile.exists()) return;

        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.initOwner(owner);
        stage.setTitle("🎬 Phát Video - " + videoFile.getName());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #11111B;");

        try {
            Media media = new Media(videoFile.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            MediaView mediaView = new MediaView(player);
            mediaView.setPreserveRatio(true);
            mediaView.fitWidthProperty().bind(root.widthProperty().subtract(40));
            mediaView.fitHeightProperty().bind(root.heightProperty().subtract(100));

            StackPane centerPane = new StackPane(mediaView);
            centerPane.setPadding(new Insets(10));
            root.setCenter(centerPane);

            HBox controls = new HBox(12);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new Insets(12));
            controls.setStyle("-fx-background-color: #181825;");

            Button btnPlay = new Button("▶ Phát");
            btnPlay.setStyle("-fx-background-color: #A6E3A1; -fx-text-fill: #1E1E2E; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");

            btnPlay.setOnAction(e -> {
                if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                    player.pause();
                    btnPlay.setText("▶ Phát");
                } else {
                    player.play();
                    btnPlay.setText("⏸ Tạm dừng");
                }
            });

            Slider timeSlider = new Slider();
            HBox.setHgrow(timeSlider, Priority.ALWAYS);

            Label lblTime = new Label("00:00");
            lblTime.setTextFill(Color.web("#CDD6F4"));
            lblTime.setFont(Font.font("SansSerif", 12));

            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!timeSlider.isValueChanging() && player.getTotalDuration() != null) {
                    timeSlider.setValue(newTime.toMillis() / player.getTotalDuration().toMillis() * 100.0);
                    int curSec = (int) newTime.toSeconds();
                    int totSec = (int) player.getTotalDuration().toSeconds();
                    lblTime.setText(String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60));
                }
            });

            timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (timeSlider.isValueChanging() && player.getTotalDuration() != null) {
                    player.seek(player.getTotalDuration().multiply(newVal.doubleValue() / 100.0));
                }
            });

            Button btnClose = new Button("Đóng");
            btnClose.setStyle("-fx-background-color: #313244; -fx-text-fill: #CDD6F4; -fx-background-radius: 8; -fx-cursor: hand;");
            btnClose.setOnAction(e -> {
                player.stop();
                stage.close();
            });

            controls.getChildren().addAll(btnPlay, timeSlider, lblTime, btnClose);
            root.setBottom(controls);

            stage.setOnCloseRequest(e -> player.stop());
            player.play();
            btnPlay.setText("⏸ Tạm dừng");

        } catch (Exception ex) {
            // Trường hợp định dạng video không được JavaFX Media hỗ trợ (VD: thiếu codec hệ thống), mở bằng app ngoài
            VBox fallbackPane = new VBox(14);
            fallbackPane.setAlignment(Pos.CENTER);
            fallbackPane.setPadding(new Insets(30));

            Label lblNotice = new Label("Trình phát JavaFX không thể giải mã trực tiếp định dạng video này.");
            lblNotice.setTextFill(Color.web("#CDD6F4"));
            lblNotice.setFont(Font.font("SansSerif", 13));

            Button btnOpenDefault = new Button("🎬 Mở bằng Trình Phát Mặc Định (VLC / Media Player)");
            btnOpenDefault.setStyle("-fx-background-color: #89B4FA; -fx-text-fill: #1E1E2E; -fx-font-weight: bold; -fx-padding: 8 16 8 16; -fx-background-radius: 8; -fx-cursor: hand;");
            btnOpenDefault.setOnAction(e -> {
                try {
                    Desktop.getDesktop().open(videoFile);
                } catch (Exception ignored) {}
            });

            Button btnClose = new Button("Đóng");
            btnClose.setStyle("-fx-background-color: #313244; -fx-text-fill: #CDD6F4; -fx-background-radius: 8;");
            btnClose.setOnAction(e -> stage.close());

            fallbackPane.getChildren().addAll(lblNotice, btnOpenDefault, btnClose);
            root.setCenter(fallbackPane);
        }

        Scene scene = new Scene(root, 720, 480);
        stage.setScene(scene);
        stage.show();
    }
}
