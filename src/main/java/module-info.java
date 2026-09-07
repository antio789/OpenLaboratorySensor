module ant.gasmeter {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires bluez.dbus;
    requires org.freedesktop.dbus;
    requires jdk.sctp;
    requires javafx.graphics;

    opens ant.gasmeter to javafx.fxml;
    exports ant.gasmeter;
    exports ant.gasmeter.BLE_Handler;
    opens ant.gasmeter.BLE_Handler to javafx.fxml;
    exports ant.gasmeter.utils;
    opens ant.gasmeter.utils to javafx.fxml;
}