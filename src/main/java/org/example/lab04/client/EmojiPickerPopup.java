package org.example.lab04.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.Arrays;
import java.util.List;

public class EmojiPickerPopup {

    private static final List<String> EMOJIS = Arrays.asList(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖",
            "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯",
            "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔",
            "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦",
            "👍", "👎", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✌️", "🤞",
            "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "🔥",
            "⭐", "🌟", "✨", "💥", "💯", "🎉", "🎊", "🚀", "💡", "⚡",
            "☕", "🍺", "🍕", "🍔", "🍰", "🎮", "📱", "💻", "🎧", "🎬"
    );

    private final Popup popup = new Popup();

    public EmojiPickerPopup(TextField targetInput) {
        popup.setAutoHide(true);

        VBox container = new VBox();
        container.setPadding(new Insets(10));
        container.setStyle("-fx-background-color: #24273A; -fx-background-radius: 12; "
                + "-fx-border-color: #45475A; -fx-border-radius: 12; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 4);");

        FlowPane flow = new FlowPane();
        flow.setHgap(6);
        flow.setVgap(6);
        flow.setPrefWrapLength(320);
        flow.setAlignment(Pos.CENTER);

        for (String emoji : EMOJIS) {
            Button btn = new Button(emoji);
            btn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-padding: 4 6 4 6; -fx-cursor: hand;");
            btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #363A4F; -fx-font-size: 18px; -fx-padding: 4 6 4 6; -fx-background-radius: 6; -fx-cursor: hand;"));
            btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-padding: 4 6 4 6; -fx-cursor: hand;"));
            btn.setOnAction(e -> {
                int caret = targetInput.getCaretPosition();
                targetInput.insertText(caret, emoji);
                targetInput.requestFocus();
                popup.hide();
            });
            flow.getChildren().add(btn);
        }

        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);
        scroll.setPrefWidth(340);
        scroll.setStyle("-fx-background: #24273A; -fx-background-color: #24273A; -fx-border-color: transparent;");

        container.getChildren().add(scroll);
        popup.getContent().add(container);
    }

    public void show(Node anchor) {
        if (popup.isShowing()) {
            popup.hide();
        } else {
            var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
            popup.show(anchor, bounds.getMinX() - 150, bounds.getMinY() - 240);
        }
    }
}
