package ant.gasmeter;

import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.BLE_Handler.BLEScanner;
import ant.gasmeter.Experiment.Creation_Config;
import ant.gasmeter.utils.ChartHandler;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO add a window to choose BTdevice if necessary
 * TODO add a window to choose BLEHost
 *
 * **/
public class Controller {
    public Pane chartPane;
    public TextField experimentNameField;
    LineChart<Number, Number> lineChart;

    private DataManagement DATA;
    @FXML
    private Label copyStatusLabel;
    @FXML
    private TextField BTstatus;

    BLEReceiver BLE;
    Thread BluetoothManager;

    @FXML
    private TextField uuidField;
    @FXML
    private TextField macField;
    @FXML
    private ComboBox<Integer> sensorCountCombo;
    @FXML
    private VBox sensorFieldsContainer;

    private List<TextField> lowerPinFields = new ArrayList<>();
    private List<TextField> upperPinFields = new ArrayList<>();



    public void initialize(DataManagement data) {
        this.DATA = data;
        ChartHandler.setupChart(data,lineChart,chartPane);
        BLE = new BLEReceiver(DATA);
        /*this.DATA.points.addListener((ListChangeListener<DataPoint>) _ -> {
            refreshChart();
        });
         */
    }

    @FXML
    protected void onCopyButtonClick() {
        String result = DATA.copy_CSV();

        copyStatusLabel.setText(result);
        if (result.startsWith("Copied")) {
            copyStatusLabel.setStyle("-fx-text-fill: green;");
        } else {
            copyStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    public void onDataClick() {
        DATA.writeStateToCSV("true");
    }

    @FXML
    public void onBTClick(){
        BLE.initializeBluetoothConnection(BTstatus);
    }

    @FXML
    public void closeBT(){
        BLE.closeConnection();
    }

    public void shutdown(){
        if (BLE != null) {
            BLE.closeConnection();
        }
        if (BluetoothManager != null && BluetoothManager.isAlive()) {
            BluetoothManager.interrupt();
        }
    }

    @FXML
    public void onBLEScanClick() {
        new BLEScanner(uuidField, macField);
    }

    @FXML
    public void onSensorCountSelected() {
        Creation_Config.Setup_Pins(sensorCountCombo,sensorFieldsContainer,lowerPinFields,upperPinFields);
    }

    public Creation_Config.BLEConnectionConfigData getBLEConnectionConfig() {
        return BLEConnectionConfig.getConfig(lowerPinFields, upperPinFields, macField, uuidField);
    }

    public TextField getBTstatus() {
        return BTstatus;
    }

    public void updateBTstatus(String text) {
        Platform.runLater(()-> BTstatus.setText(text));
    }

}
