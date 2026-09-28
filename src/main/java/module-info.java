module org.example.lab04 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;   // ImageIO để tạo thumbnail ảnh
    requires webcam.capture; // Cho video call

    opens org.example.lab04.client to javafx.fxml;
    opens org.example.lab04.network to javafx.fxml;
    opens org.example.lab04.server to javafx.fxml;

    exports org.example.lab04.client;
    exports org.example.lab04.network;
    exports org.example.lab04.server;
}