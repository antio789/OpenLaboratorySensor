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
    private final LineChart<Number, Number>  lineChart;
    private final ExperimentFileManager fileManager;
    private final List<XYChart.Series<Number, Number>> series = new ArrayList<>();

    public ChartHandler(ExperimentFileManager data, Pane chartPane) {
        fileManager = data;
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Days");
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
                        for (DataPoint point : added) {
                            Duration duration = Duration.between(fileManager.experimentStartTime,point.timestamp());
                            double days = duration.toDays();
                            if (point.sensor() >= 1 && point.sensor() <= series.size()) {
                                series.get(point.sensor() - 1).getData().add(new XYChart.Data<>(days,point.value()));
                            }
                        }
                    });
                }
            }
        });
    }

    public List<XYChart.Series<Number,Number>> populateseries() throws IOException {
        List<List<String>> records = fileManager.readCsvData();
        if(records.isEmpty()) throw new IOException("No data found");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd_MM_yy_HH_mm_ss");
        // event-log columns: 0=sensor, 1=count, 2=time, 3=volume
        List<XYChart.Series<Number, Number>> result = new ArrayList<>();
        for (int i = 1; i <= fileManager.getSensorCount(); i++) {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName("Sensor " + i);
            double volume = 0;
            for (List<String> record : records) {
                if (Integer.parseInt(record.get(0)) != i) continue;
                LocalDateTime time = LocalDateTime.parse(record.get(2), formatter);
                Duration duration = Duration.between(fileManager.experimentStartTime, time);
                double days = duration.toDays();
                volume += Double.parseDouble(record.get(3));
                s.getData().add(new XYChart.Data<>(days, volume));
            }
            result.add(s);
        }
        return result;
    }
}
