package ant.gasmeter.Experiment;

import ant.gasmeter.ApplicationState;
import ant.gasmeter.ApplicationState.TabData;
import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.utils.ExperimentFileManager;
import ant.gasmeter.utils.Logger;
import javafx.event.ActionEvent;
import javafx.scene.chart.LineChart;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;

import java.io.File;

public class TabControl {
    public Pane chartPane; //<Pane fx:id="chartPane" prefWidth="500" prefHeight="300" minWidth="500" minHeight="300"/>
    public TextArea logArea;
    public TextField BTstatus;
    private String Name;
    TabData initData;
    ApplicationState state;
    LineChart<Number, Number> lineChart;
    private BLEReceiver BLE;
    ExperimentFileManager fileManager;
    ChartHandler chart;
    Logger logger;

    public void initialise(TabData data, String name){
        Name=name;
        initData = data;
        logger = new Logger(name, logArea);
        logger.log("experiment created with " + data.resolutions().size() + " sensor(s)");
        fileManager = new ExperimentFileManager(name, data.resolutions(), data.lowerPins(), data.upperPins());
        chart = new ChartHandler(fileManager, chartPane);
        state = ApplicationState.getInstance();
    }


    public void connect(){
        BLE = new BLEReceiver(fileManager, logger);
        state.getBleConnections().add(BLE);
        logger.log("starting bluetooth connection to " + state.getTabData(Name).macAddress());
        BLE.initializeBluetoothConnection(BTstatus, state.getTabData(Name).macAddress());
    }

    public void oncloseBT(ActionEvent actionEvent) {
        if (BLE == null) return; // no connection was ever started
        BLE.closeConnection();
        logger.log("bluetooth connection closed");
        // BTstatus is bound to the connection task's messages, unbind before setting text directly
        BTstatus.textProperty().unbind();
        BTstatus.setText("Bluetooth connection closed");
    }

    public void onAddFakeData(ActionEvent actionEvent) {
        fileManager.addFakeDataPoint();
        logger.log("fake data point added");
    }

    public void onExport(ActionEvent actionEvent) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export experiment data");
        chooser.setInitialDirectory(fileManager.getLogDirectory().toFile());
        chooser.setInitialFileName(fileManager.getExportFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File target = chooser.showSaveDialog(chartPane.getScene().getWindow());
        if (target != null) {
            logger.log(fileManager.exportCsvFile(target.toPath()));
        }
    }

}
