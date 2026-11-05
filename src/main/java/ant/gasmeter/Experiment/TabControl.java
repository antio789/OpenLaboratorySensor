package ant.gasmeter.Experiment;

import ant.gasmeter.ApplicationState;
import ant.gasmeter.ApplicationState.TabData;
import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.utils.ExperimentFileManager;
import javafx.event.ActionEvent;
import javafx.scene.chart.LineChart;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;

import java.io.File;

public class TabControl {
    public Pane chartPane; //<Pane fx:id="chartPane" prefWidth="500" prefHeight="300" minWidth="500" minHeight="300"/>
    public TextField BTstatus;
    private String Name;
    TabData initData;
    ApplicationState state;
    LineChart<Number, Number> lineChart;
    private BLEReceiver BLE;
    ExperimentFileManager fileManager;
    ChartHandler chart;

    public void initialise(TabData data, String name){
        Name=name;
        initData = data;
        fileManager = new ExperimentFileManager(name, data.resolutions(), data.lowerPins(), data.upperPins());
        chart = new ChartHandler(fileManager, chartPane);
        state = ApplicationState.getInstance();
    }


    public void connect(){
        BLE = new BLEReceiver(fileManager);
        state.getBleConnections().add(BLE);
        BLE.initializeBluetoothConnection(BTstatus, state.getTabData(Name).macAddress());
    }

    public void oncloseBT(ActionEvent actionEvent) {
        BLE.closeConnection();
    }

    public void onAddFakeData(ActionEvent actionEvent) {
        fileManager.addFakeDataPoint();
    }

    public void onExport(ActionEvent actionEvent) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export experiment data");
        chooser.setInitialDirectory(fileManager.getLogDirectory().toFile());
        chooser.setInitialFileName(fileManager.getExportFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File target = chooser.showSaveDialog(chartPane.getScene().getWindow());
        if (target != null) {
            System.out.println(fileManager.exportCsvFile(target.toPath()));
        }
    }

}
