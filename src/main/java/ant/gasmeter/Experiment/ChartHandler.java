package ant.gasmeter.Experiment;

import ant.gasmeter.utils.ExperimentFileManager;
import ant.gasmeter.utils.DataPoint;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChartHandler {
    private static final double HOURS_BEFORE_DAYS = 48; // show hours until the data spans 2 days
    private final LineChart<Number, Number>  lineChart;
    private final NumberAxis xAxis;
    private final ExperimentFileManager fileManager;
    private final List<XYChart.Series<Number, Number>> series = new ArrayList<>();
    private boolean usingDays = false;

    public ChartHandler(ExperimentFileManager data, Pane chartPane) {
        fileManager = data;
        xAxis = new NumberAxis();
        xAxis.setLabel("Hours");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Gas Volume");

        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Live Gas Volume Chart");
        lineChart.setPrefSize(500, 300);

        for (int i = 1; i <= data.getSensorCount(); i++) {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName("Sensor " + i);
            series.add(s);
            lineChart.getData().add(s);
        }
        chartPane.getChildren().add(lineChart);

        fileManager.dataPoints.addListener((ListChangeListener<DataPoint>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    List<DataPoint> added = List.copyOf(change.getAddedSubList());
                    Platform.runLater(() ->{
                        if (shouldSwitchToDays(added)) {
                            switchToDays();
                        }
                        for (DataPoint point : added) {
                            if (point.sensor() >= 1 && point.sensor() <= series.size()) {
                                series.get(point.sensor() - 1).getData().add(new XYChart.Data<>(toAxisValue(point.timestamp()), point.value()));
                            }
                        }
                    });
                }
            }
        });
    }

    //x value on the axis: hours at the start of the experiment, days once it runs long enough
    private double toAxisValue(LocalDateTime timestamp){
        Duration duration = Duration.between(fileManager.experimentStartTime, timestamp);
        return usingDays ? duration.toHours() / 24.0 : duration.toHours();
    }

    private boolean shouldSwitchToDays(List<DataPoint> added){
        if (usingDays) return false;
        LocalDateTime last = added.getLast().timestamp();
        return Duration.between(fileManager.experimentStartTime, last).toHours() >= HOURS_BEFORE_DAYS;
    }

    //rescales all existing points to the new unit and updates the axis label
    private void switchToDays(){
        usingDays = true;
        xAxis.setLabel("Days");
        for (int i = 0; i < series.size(); i++) {
            XYChart.Series<Number, Number> s = series.get(i);
            s.getData().clear();
            for (DataPoint point : fileManager.dataPoints) {
                if (point.sensor() == i + 1) {
                    s.getData().add(new XYChart.Data<>(toAxisValue(point.timestamp()), point.value()));
                }
            }
        }
    }

    public List<XYChart.Series<Number,Number>> populateseries() throws IOException {
        List<List<String>> records = fileManager.readCsvData();
        if(records.isEmpty()) throw new IOException("No data found");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd_MM_yy_HH_mm_ss");
        // event-log columns: 0=sensor, 1=count, 2=time, 3=volume
        LocalDateTime newest = LocalDateTime.parse(records.getLast().get(2), formatter);
        usingDays = Duration.between(fileManager.experimentStartTime, newest).toHours() >= HOURS_BEFORE_DAYS;
        List<XYChart.Series<Number, Number>> result = new ArrayList<>();
        for (int i = 1; i <= fileManager.getSensorCount(); i++) {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName("Sensor " + i);
            for (List<String> record : records) {
                if (Integer.parseInt(record.get(0)) != i) continue;
                LocalDateTime time = LocalDateTime.parse(record.get(2), formatter);
                // volume column is cumulative, use it directly
                s.getData().add(new XYChart.Data<>(toAxisValue(time), Double.parseDouble(record.get(3))));
            }
            result.add(s);
        }
        return result;
    }
}
