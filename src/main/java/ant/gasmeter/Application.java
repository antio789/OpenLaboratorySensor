package ant.gasmeter;

import ant.gasmeter.BLE_Handler.BLEReceiver;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/**
 * TODO add a warning when closing the application
 * TODO add option to open from file
 * TODO add multiple input source support
 * TODO add support for USB arduino
 * TODO add support for direct rPI direct input
 * TODO add proper logger
 * TODO push code to arduino if possible
 *
 */
public class Application extends javafx.application.Application {
    Controller controller;
    @Override

    //
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(Application.class.getResource("view.fxml"));
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double width = screenBounds.getWidth() * 0.5;
        double height = screenBounds.getHeight() * 0.5;
        Scene scene = new Scene(fxmlLoader.load(), width, height);
        controller = fxmlLoader.getController();

        controller.initialize();
        URL stylesheet = getClass().getResource("Styles.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        stage.setTitle("Gas Meter");
        stage.setScene(scene);
        stage.show();
    }




    @Override
    public void stop() throws Exception {
        controller.shutdown();
        super.stop();
    }
}