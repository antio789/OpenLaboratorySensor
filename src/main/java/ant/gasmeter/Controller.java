package ant.gasmeter;

import ant.gasmeter.BLE_Handler.BLEReceiver;
import ant.gasmeter.BLE_Handler.BLEScanner;
import ant.gasmeter.utils.DataPoint;
import ant.gasmeter.utils.Ref;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * TODO add a window to choose BTdevice if necessary
 * TODO add a window to choose BLEHost
 *
 * **/
public class Controller {
    public Pane chartPane;
    public TextField experimentNameField;

    private DataManagement DATA;
    private LineChart<Number, Number> lineChart;
    @FXML
    private Label copyStatusLabel;
    @FXML
    private TextField BTstatus;

    BLEReceiver BLE;
    Thread BluetoothManager;


    public void initialize(DataManagement data) {
        this.DATA = data;
        setupChart(data);
        BLE = new BLEReceiver(DATA);
        /*this.DATA.points.addListener((ListChangeListener<DataPoint>) _ -> {
            refreshChart();
        });
         */
    }



    @FXML
    protected void onCopyButtonClick() {
        String result = DATA.copy_CSV();  // call your method

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
    private TextField uuidField;
    @FXML
    private TextField device_name;
    @FXML
    public void onBLEScanClick() {
        new BLEScanner(uuidField, device_name);
    }

    @FXML
    private ComboBox<Integer> sensorCountCombo;
    @FXML
    private VBox sensorFieldsContainer;
    public void onSensorCountSelected() {
        Integer count = sensorCountCombo.getValue();
        if (count == null) return;

        Platform.runLater(() -> {
            sensorFieldsContainer.getChildren().clear();

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
            }
        });
    }

    public void setupChart(DataManagement data) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Days");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Gas Flow");

        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Live Gas Flow Chart");
        lineChart.setPrefSize(500, 300);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Sensor Data");

        //populateserie(series,data);
        lineChart.getData().add(series);
        chartPane.getChildren().add(lineChart);

        data.points.addListener((ListChangeListener<DataPoint>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    List<DataPoint> added = List.copyOf(change.getAddedSubList());
                    Platform.runLater(() ->{
                        for (DataPoint point : added) {
                            Duration duration = Duration.between(data.BaseTime,point.timestamp());
                            double days = duration.toDays();
                            series.getData().add(new XYChart.Data<>(days,point.value()));
                        }
                    });
                }
            }
        });
    }

    private void refreshChart(){
        System.out.println("Refreshing chart");
        Task<XYChart.Series<Number,Number>> task = new Task<>() {
            @Override
            protected XYChart.Series<Number,Number> call() throws Exception {
                return populateserie(DATA);
            }

            @Override
            protected void succeeded() {
                lineChart.getData().clear();
                XYChart.Series<Number, Number> series = this.getValue();
                series.setName("Sensor Data");
                lineChart.getData().add(series);
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public XYChart.Series<Number,Number> populateserie(DataManagement datasource) throws IOException {
        List<List<String>> values = datasource.readCsvData();
        if(values.isEmpty()) throw new IOException("No data found");
        float gasflow =0;
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for(List<String> point : values){
            String date = point.getFirst();
            String value = point.get(1);
            if(value.equalsIgnoreCase( "true")){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MM yy, k:m");
                LocalDateTime time = LocalDateTime.parse(date,formatter);
                Duration duration = Duration.between(datasource.BaseTime,time);
                float days = duration.toDays();
                gasflow+= Ref.VOLUMETRIC_RESOLUTION;
                series.getData().add(new XYChart.Data<>(days,gasflow));
            }
        }
        return series;
    }

    public TextField getBTstatus() {
        return BTstatus;
    }

    public void updateBTstatus(String text) {
        Platform.runLater(()-> BTstatus.setText(text));
    }

}