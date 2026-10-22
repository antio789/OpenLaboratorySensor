package ant.gasmeter;

import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.BLE_Handler.BLEScanner;
import ant.gasmeter.Experiment.TabControl;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * **/
public class Controller {
    public Pane chartPane;
    public TextField experimentNameField;

    @FXML
    private Label validationLabel;
    @FXML
    private TextField BTstatus;
    @FXML
    private TabPane tabPane;

    Thread BluetoothManager;

    @FXML
    private TextField macField;
    @FXML
    private ComboBox<Integer> sensorCountCombo;
    @FXML
    private VBox sensorFieldsContainer;

    private final List<TextField> lowerPinFields = new ArrayList<>();
    private final List<TextField> upperPinFields = new ArrayList<>();

    ApplicationState App_state;

    public void initialize() {
        App_state = ApplicationState.getInstance();
    }


    @FXML
    public void onBTClick(){
        //BLE.initializeBluetoothConnection(BTstatus);
    }
    @FXML //TODO: this needs to be attached to every specific experiment.
    public void closeBT(){
        //ble.closeConnection();
    }

    public void shutdown(){
        for (BLEReceiver ble : ApplicationState.getInstance().getBleConnections()) {
            if (ble != null) {
                ble.closeConnection();
            }
        }
        if (BluetoothManager != null && BluetoothManager.isAlive()) {
            BluetoothManager.interrupt();
        }
    }

    @FXML
    public void onBLEScanClick() {
        new BLEScanner(macField);
    }

    @FXML
    public void onContinueClick() throws IOException {
        String experiment_name = experimentNameField.getText();
        ApplicationState state = ApplicationState.getInstance();

        if (handleContinue(experiment_name)){
            FXMLLoader loader = new FXMLLoader(Application.class.getResource("Experiment.fxml"));
            loader.load();
            Tab newTab = new Tab();
            newTab.setText(experiment_name);
            TabControl experimentcontroller = loader.getController();
            experimentcontroller.initialise(state.getTabData(experiment_name),experiment_name);
            newTab.setContent(loader.getRoot());
            tabPane.getTabs().add(newTab);
        }
    }

    public boolean handleContinue(String tabname) {
        ApplicationState state = ApplicationState.getInstance();

        boolean fieldsValid = isFieldValid(macField);
        for (TextField field : lowerPinFields) fieldsValid = isFieldValid(field) && fieldsValid;
        for (TextField field : upperPinFields) fieldsValid = isFieldValid(field) && fieldsValid;

        if (fieldsValid) {
            if(!state.setTabData(tabname, new ApplicationState.TabData(macField.getText(),
                    lowerPinFields.stream().map(TextField::getText).toList(),
                    upperPinFields.stream().map(TextField::getText).toList()))){
                validationLabel.setStyle("-fx-text-fill: red;");
                validationLabel.setText("Experiment name already in use please use another one");
            }else {
                validationLabel.setStyle("-fx-text-fill: green;");
                validationLabel.setText("All fields valid!");
                return true;
            }
        } else{
            validationLabel.setStyle("-fx-text-fill: red;");
            validationLabel.setText("Please fill all required fields!");
        }
        return false;
    }

    private boolean isFieldValid(TextField field) {
        boolean valid = field.getText() != null && !field.getText().isEmpty();
        field.setStyle(valid ? "" : "-fx-control-inner-background: #ffdddd; -fx-border-color: red;");
        return valid;
    }

    @FXML
    public void onSensorCountSelected() {
        Integer count = sensorCountCombo.getValue();
        if (count == null) return;

        Platform.runLater(() -> {
            sensorFieldsContainer.getChildren().clear();
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
            sensorFieldsContainer.getChildren().add(headerBox);

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
                sensorFieldsContainer.getChildren().add(sensorBox);

                lowerPinFields.add(lowerField);
                upperPinFields.add(upperField);
            }
        });
    }
}
