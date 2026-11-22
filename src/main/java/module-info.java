module org.example.lab04 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.net.http;
    requires java.desktop;
    requires com.google.gson;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.bson;
    requires org.mongodb.driver.core;

    // Cho phép JavaFX và Gson truy cập vào các package
    opens org.example.lab04.client to javafx.fxml, com.google.gson;
    opens org.example.lab04.network to javafx.fxml, com.google.gson;
    opens org.example.lab04.server to javafx.fxml, com.google.gson;
    opens org.example.lab04.model to javafx.fxml, com.google.gson;
    opens org.example.lab04.service to javafx.fxml, com.google.gson;

    // Xuất bản các package này để hệ thống JavaFX có thể chạy được
    exports org.example.lab04.client;
    exports org.example.lab04.network;
    exports org.example.lab04.server;
    exports org.example.lab04.model;
    exports org.example.lab04.service;
}
