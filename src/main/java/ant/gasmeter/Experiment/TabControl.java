package ant.gasmeter.Experiment;

import ant.gasmeter.ApplicationState;
import ant.gasmeter.ApplicationState.TabData;
import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.utils.ExperimentFileManager;
import ant.gasmeter.utils.Logger;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;

import java.io.File;

public class TabControl {
    public Pane chartPane; //<Pane fx:id="chartPane" prefWidth="500" prefHeight="300" minWidth="500" minHeight="300"/>
    public TextArea logArea;
    public Label btStatus;
    private Tab tab;
    private String Name;
    ApplicationState state;
    private BLEReceiver BLE;
    ExperimentFileManager fileManager;
    ChartHandler chart;
    Logger logger;

    public void initialise(TabData data, String name, Tab tab){
        Name=name;
        this.tab = tab;
        logger = new Logger(name, logArea);
        logger.log("experiment created with " + data.resolutions().size() + " sensor(s)");
        fileManager = new ExperimentFileManager(name, data.resolutions(), data.lowerPins(), data.upperPins());
        chart = new ChartHandler(fileManager, chartPane);
        state = ApplicationState.getInstance();
    }

    public String getExperimentName(){
        return Name;
    }

    public java.time.Duration getRunningDuration(){
        return java.time.Duration.between(fileManager.experimentStartTime, java.time.LocalDateTime.now());
    }

    public boolean isBluetoothConnected(){
        return BLE != null && BLE.isConnected();
    }

    public void connect(){
        if (BLE != null) {
            // a previous connection attempt is still around, stop it before starting a new one
            state.getBleConnections().remove(BLE);
            BLE.closeConnection();
        }
        BLE = new BLEReceiver(fileManager, logger);
        state.getBleConnections().add(BLE);
        logger.log("starting bluetooth connection to " + state.getTabData(Name).macAddress());
        BLE.initializeBluetoothConnection(btStatus, state.getTabData(Name).macAddress());
    }

    public void oncloseBT(ActionEvent actionEvent) {
        if (BLE == null) return;
        BLE.closeConnection(); // also settles the status field
        logger.log("bluetooth connection closed");
    }

    public void onAddFakeData(ActionEvent actionEvent) {
        fileManager.addFakeDataPoint();
        logger.log("fake data point added");
    }

    public void onExport(ActionEvent actionEvent) {
        doExport();
    }

    private void doExport(){
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

    public void onCloseExperiment(ActionEvent actionEvent) {
        // 1. confirm
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Close this experiment? The bluetooth connection will be closed.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Close experiment " + Name);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        // 2. reset the board totals for the next experiment, then close the bluetooth connection
        if (BLE != null) {
            BLE.resetDevice();
            state.getBleConnections().remove(BLE);
            BLE.closeConnection();
        }

        // 3. offer a final export
        Alert export = new Alert(Alert.AlertType.CONFIRMATION,
                "Export the data before closing?",
                ButtonType.YES, ButtonType.NO);
        export.setHeaderText("Export");
        if (export.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            doExport();
        }

        // 4. log the closure and close the tab
        logger.log("experiment closed");
        state.removeTabData(Name); // free the experiment name for reuse
        if (tab != null && tab.getTabPane() != null) {
            tab.getTabPane().getTabs().remove(tab);
        }
    }
}
