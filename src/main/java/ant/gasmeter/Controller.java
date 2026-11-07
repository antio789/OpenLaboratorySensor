package ant.gasmeter;

import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.BLE_Handler.BLEScanner;
import ant.gasmeter.Experiment.TabControl;
import ant.gasmeter.utils.ConfigManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * **/
public class Controller {
    public TextField experimentNameField;
    public VBox dashboardRoot;

    @FXML
    private Label validationLabel;
    @FXML
    private TabPane tabPane;

    Thread BluetoothManager;

    @FXML
    private TextField macField;
    @FXML
    private CheckBox saveConfigCheck;
    @FXML
    private ImageView logoView;
    @FXML
    private VBox experimentsContainer;
    @FXML
    private Label noExperimentsLabel;

    //one dashboard card per open experiment tab
    private final Map<Tab, VBox> experimentCards = new HashMap<>();
    @FXML
    private ComboBox<Integer> sensorCountCombo;
    @FXML
    private VBox sensorFieldsContainer;

    private final List<TextField> lowerPinFields = new ArrayList<>();
    private final List<TextField> upperPinFields = new ArrayList<>();
    private final List<TextField> resolutionFields = new ArrayList<>();

    ApplicationState App_state;

    public void initialize() {
        App_state = ApplicationState.getInstance();
        setupLogo();
        setupDashboardUpdater();
    }

    //loads the logo from the resources; replace logo.png to change it
    private void setupLogo(){
        Image logo = new Image(Application.class.getResource("logo.png").toExternalForm());
        logoView.setImage(logo);
    }

    //refreshes the dashboard cards every second: running time and bluetooth status
    private void setupDashboardUpdater(){
        Timeline updater = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateDashboard()));
        updater.setCycleCount(Timeline.INDEFINITE);
        updater.play();
    }

    private void updateDashboard(){
        List<Tab> experimentTabs = tabPane.getTabs().stream()
                .filter(tab -> !tab.getText().equals("New Experiment") && !tab.getText().equals("Dashboard"))
                .toList();
        // remove the cards of closed experiments from the map and from the container
        experimentCards.entrySet().removeIf(entry -> {
            if (!experimentTabs.contains(entry.getKey())) {
                experimentsContainer.getChildren().remove(entry.getValue());
                return true;
            }
            return false;
        });
        for (Tab tab : experimentTabs) {
            if (!(tab.getUserData() instanceof TabControl control)) continue;
            VBox card = experimentCards.get(tab);
            if (card == null) {
                card = createExperimentCard(tab, control);
                experimentCards.put(tab, card);
                experimentsContainer.getChildren().add(card);
            }
            updateExperimentCard(card, control);
        }
        noExperimentsLabel.setVisible(experimentTabs.isEmpty());
        noExperimentsLabel.setManaged(experimentTabs.isEmpty());
    }

    private VBox createExperimentCard(Tab tab, TabControl control){
        VBox card = new VBox(5);
        card.getStyleClass().add("experiment-card");
        Label title = new Label(control.getExperimentName());
        title.getStyleClass().add("card-title");
        Label runningTime = new Label();
        runningTime.getStyleClass().add("card-subtext");
        Label status = new Label();
        card.getChildren().addAll(title, runningTime, status);
        // clicking the card opens the experiment tab
        card.setOnMouseClicked(event -> tabPane.getSelectionModel().select(tab));
        return card;
    }

    private void updateExperimentCard(VBox card, TabControl control){
        Label runningTime = (Label) card.getChildren().get(1);
        Label status = (Label) card.getChildren().get(2);
        runningTime.setText("running for " + formatDuration(control.getRunningDuration()));
        if (control.isBluetoothConnected()) {
            status.setText("● Bluetooth connected");
            status.getStyleClass().clear();
            status.getStyleClass().add("card-status-ok");
        } else {
            status.setText("● Bluetooth disconnected");
            status.getStyleClass().clear();
            status.getStyleClass().add("card-status-down");
        }
    }

    private String formatDuration(java.time.Duration duration){
        long seconds = duration.getSeconds();
        return String.format("%dd %02dh %02dm %02ds",
                seconds / 86400, (seconds % 86400) / 3600, (seconds % 3600) / 60, seconds % 60);
    }


    @FXML
    public void onBTClick(){
        //BLE.initializeBluetoothConnection(btStatus);
    }
    @FXML //
    public void closeBT(){
        //ble.closeConnection();
    }

    //TRUE if at least one experiment tab is open
    public boolean hasRunningExperiments(){
        for (Tab tab : tabPane.getTabs()) {
            if (!tab.getText().equals("New Experiment") && !tab.getText().equals("Dashboard")) return true;
        }
        return false;
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

    //opens a dialog with the saved configurations and fills the form with the selected one
    @FXML
    public void onLoadConfigClick() {
        List<String> configs = ConfigManager.listConfigs();
        if (configs.isEmpty()) {
            validationLabel.setStyle("-fx-text-fill: red;");
            validationLabel.setText("No saved configurations found");
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(configs.getFirst(), configs);
        dialog.setHeaderText("Load configuration");
        dialog.setContentText("Configuration:");
        Optional<String> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() == null) return;
        try {
            ApplicationState.TabData data = ConfigManager.loadConfig(choice.get());
            macField.setText(data.macAddress());
            sensorCountCombo.setValue(data.resolutions().size()); // rebuilds the sensor rows
            // the rows are built on the FX thread, fill them once they exist
            Platform.runLater(() -> {
                for (int i = 0; i < data.resolutions().size(); i++) {
                    lowerPinFields.get(i).setText(data.lowerPins().get(i));
                    upperPinFields.get(i).setText(data.upperPins().get(i));
                    resolutionFields.get(i).setText(String.valueOf(data.resolutions().get(i)));
                }
                validationLabel.setStyle("-fx-text-fill: green;");
                validationLabel.setText("Configuration '" + choice.get() + "' loaded, adjust the experiment name and continue");
            });
        } catch (Exception ex) {
            validationLabel.setStyle("-fx-text-fill: red;");
            validationLabel.setText("Failed to load configuration: " + ex.getMessage());
        }
    }

    @FXML
    public void onContinueClick() throws IOException {
        String experiment_name = experimentNameField.getText();
        ApplicationState state = ApplicationState.getInstance();

        if (handleContinue(experiment_name)){
            if (saveConfigCheck.isSelected()) {
                try {
                    ConfigManager.saveConfig(experiment_name, state.getTabData(experiment_name));
                } catch (IOException ex) {
                    validationLabel.setStyle("-fx-text-fill: red;");
                    validationLabel.setText("Experiment started, but saving the configuration failed: " + ex.getMessage());
                }
            }
            FXMLLoader loader = new FXMLLoader(Application.class.getResource("Experiment.fxml"));
            loader.load();
            Tab newTab = new Tab();
            newTab.setText(experiment_name);
            newTab.setClosable(false); // closing is done via the Close Experiment button
            TabControl experimentcontroller = loader.getController();
            experimentcontroller.initialise(state.getTabData(experiment_name),experiment_name,newTab);
            newTab.setUserData(experimentcontroller); // used by the dashboard cards
            newTab.setContent(loader.getRoot());
            tabPane.getTabs().add(newTab);
        }
    }

    public boolean handleContinue(String tabname) {
        ApplicationState state = ApplicationState.getInstance();

        boolean fieldsValid = isFieldValid(macField);
        for (TextField field : lowerPinFields) fieldsValid = isFieldValid(field) && fieldsValid;
        for (TextField field : upperPinFields) fieldsValid = isFieldValid(field) && fieldsValid;
        for (TextField field : resolutionFields) fieldsValid = isFieldValid(field) && fieldsValid;

        List<Double> resolutions = new ArrayList<>();
        for (TextField field : resolutionFields) {
            try {
                resolutions.add(Double.parseDouble(field.getText()));
            } catch (NumberFormatException e) {
                field.setStyle("-fx-control-inner-background: #ffdddd; -fx-border-color: red;");
                fieldsValid = false;
            }
        }

        if (fieldsValid) {
            if(!state.setTabData(tabname, new ApplicationState.TabData(macField.getText(), lowerPinFields.stream().map(TextField::getText).toList(),
                    upperPinFields.stream().map(TextField::getText).toList(), resolutions))){
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
            resolutionFields.clear();

            Label headerSensor = new Label("Sensor Pins");
            headerSensor.setMaxWidth(120);
            headerSensor.setMinWidth(120);
            Label headerLower = new Label("Lower");
            headerLower.setMinWidth(130);
            Label headerHigher = new Label("Higher");
            headerHigher.setMinWidth(130);
            Label headerResolution = new Label("Volumetric Resolution");
            headerResolution.setMinWidth(130);

            Label helpIcon = new Label("?");
            helpIcon.setStyle("-fx-background-radius: 50%; -fx-background-color: lightblue; -fx-padding: 5px; -fx-font-size: 14; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: CENTER;");
            helpIcon.setTooltip(new Tooltip("Please select the pins based on the numbering on the board \n Make sure to Correctly identify which pin is for the lower or higher sensor on the Gas Meter \n The volumetric resolution is the volume of gas produced per cycle for this sensor (e.g. 4.5)"));

            HBox headerBox = new HBox(10, headerSensor, headerLower, headerHigher, headerResolution, helpIcon);
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

                TextField resolutionField = new TextField();
                resolutionField.setPromptText("Volume per cycle");
                resolutionField.setMaxWidth(130);

                sensorBox.getChildren().addAll(sensorLabel, lowerField, upperField, resolutionField);
                sensorFieldsContainer.getChildren().add(sensorBox);

                lowerPinFields.add(lowerField);
                upperPinFields.add(upperField);
                resolutionFields.add(resolutionField);
            }
        });
    }
}
