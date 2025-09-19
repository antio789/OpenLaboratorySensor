package ant.gasmeter;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

public class Application extends javafx.application.Application {
    DataManagement data;
    Controller controller;
    @Override
    public void start(Stage stage) throws IOException {
        data = new DataManagement();

        FXMLLoader fxmlLoader = new FXMLLoader(Application.class.getResource("view.fxml"));
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double width = screenBounds.getWidth() * 0.5;
        double height = screenBounds.getHeight() * 0.5;
        Scene scene = new Scene(fxmlLoader.load(), width, height);
        controller = fxmlLoader.getController();
        controller.setDataManagement(data);

        scene.getStylesheets().add(getClass().getResource("Styles.css").toExternalForm());
        stage.setTitle("Gas Meter");
        stage.setScene(scene);
        stage.show();

        controller.setupBT();
    }

    @Override
    public void stop() throws Exception {
        controller.shutdown();
        super.stop();
    }
}