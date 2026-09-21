package ant.gasmeter.Experiment;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class Creation_Config {
    public record BLEConnectionConfigData(String mac, String uuid, List<SensorPins> sensorPins) {}
    public record SensorPins(String lower, String upper) {}

    public static void Setup_Pins(ComboBox<Integer> sensorCountCombo, VBox sensorcontainer, List<TextField> lowerPinFields, List<TextField> upperPinFields){
        Integer count = sensorCountCombo.getValue();
        if (count == null) return;

        Platform.runLater(() -> {
            sensorcontainer.getChildren().clear();
            lowerPinFields.clear();
            upperPinFields.clear();

            Label headerSensor = new Label("Sensor Pins");
            headerSensor.setMaxWidth(120);
            headerSensor.setMinWidth(120);
            Label headerLower = new Label("Lower");
            headerLower.setMinWidth(130);
            Label headerHigher = new Label("Higher");
            headerHigher.setMinWidth(130);

            Label helpIcon = new Label("?");
            helpIcon.setStyle("-fx-background-radius: 50%; -fx-background-color: lightblue; -fx-padding: 5px; -fx-font-size: 14; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: CENTER;");
            helpIcon.setTooltip(new Tooltip("Please select the pins based on the numbering on the board \n Make sure to Correctly identify which pin is for the lower or higher sensor on the Gas Meter"));

            HBox headerBox = new HBox(10, headerSensor, headerLower, headerHigher, helpIcon);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            sensorcontainer.getChildren().add(headerBox);

            for (int i = 1; i <= count; i++) {
                HBox sensorBox = new HBox(10);
                sensorBox.setAlignment(Pos.CENTER_LEFT);

                Label sensorLabel = new Label("Sensor " + i + ":");
                sensorLabel.setMaxWidth(120);
                sensorLabel.setMinWidth(120);

                TextField lowerField = new TextField();
                lowerField.setPromptText("Lower sensor pin");
                lowerField.setMaxWidth(130);

                TextField upperField = new TextField();
                upperField.setPromptText("Upper sensor pin");
                upperField.setMaxWidth(130);

                sensorBox.getChildren().addAll(sensorLabel, lowerField, upperField);
                sensorcontainer.getChildren().add(sensorBox);

                lowerPinFields.add(lowerField);
                upperPinFields.add(upperField);
            }
        });
    }

    public BLEConnectionConfigData getBLEConnectionConfig(List<TextField> lowerPinFields,List<TextField> upperPinFields, TextField macField, TextField uuidField) {
        List<Creation_Config.SensorPins> pins = new ArrayList<>();
        for (int i = 0; i < lowerPinFields.size(); i++) {
            pins.add(new Creation_Config.SensorPins(
                    lowerPinFields.get(i).getText(),
                    upperPinFields.get(i).getText()
            ));
        }
        return new BLEConnectionConfigData(
                macField.getText(),
                uuidField.getText(),
                pins
        );
    }

    public static boolean validateFields(TextField macField, TextField uuidField, List<TextField> lowerPinFields, List<TextField> upperPinFields) {
        boolean allValid = true;
        
        allValid = checkField(macField) && allValid;
        allValid = checkField(uuidField) && allValid;
        for (TextField field : lowerPinFields) {
            allValid = checkField(field) && allValid;
        }
        for (TextField field : upperPinFields) {
            allValid = checkField(field) && allValid;
        }
        
        return allValid;
    }

    private static boolean checkField(TextField field) {
        boolean valid = field.getText() != null && !field.getText().isEmpty();
        field.setStyle(valid ? "" : "-fx-control-inner-background: #ffdddd; -fx-border-color: red;");
        return valid;
    }

    public static void handleContinue(TextField macField, TextField uuidField, List<TextField> lowerPinFields, List<TextField> upperPinFields, Label validationLabel, Runnable onSuccess) {
        boolean allValid = validateFields(macField, uuidField, lowerPinFields, upperPinFields);
        if (allValid) {
            validationLabel.setStyle("-fx-text-fill: green;");
            validationLabel.setText("All fields valid!");
            onSuccess.run();
        } else {
            validationLabel.setStyle("-fx-text-fill: red;");
            validationLabel.setText("Please fill all required fields!");
        }
    }
}
