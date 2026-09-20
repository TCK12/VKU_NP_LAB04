module org.example.lab04 {
    requires javafx.controls;
    requires javafx.fxml;

    // Cho phép JavaFX truy cập vào package chứa giao diện (nếu bạn dùng FXML)
    opens org.example.lab04.client to javafx.fxml;
    opens org.example.lab04.network to javafx.fxml;
    opens org.example.lab04.server to javafx.fxml;

    // Xuất bản các package này để hệ thống JavaFX có thể chạy được
    exports org.example.lab04.client;
    exports org.example.lab04.network;
    exports org.example.lab04.server;
}