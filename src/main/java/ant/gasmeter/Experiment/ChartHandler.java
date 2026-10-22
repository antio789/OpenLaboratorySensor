package ant.gasmeter.Experiment;

import ant.gasmeter.utils.ExperimentFileManager;
import ant.gasmeter.utils.DataPoint;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

//TODO: make it work with multiple lines at the same time, and support more than one experiment as it still uses datamanagement
public class ChartHandler {
    private final LineChart<Number, Number>  lineChart;
    private final ExperimentFileManager fileManager;

    public ChartHandler(ExperimentFileManager data, Pane chartPane) {
        fileManager = data;
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Days");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Gas Flow");

        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Live Gas Flow Chart");
        lineChart.setPrefSize(500, 300);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Sensor Data");

        lineChart.getData().add(series);
        chartPane.getChildren().add(lineChart);

        fileManager.points.addListener((ListChangeListener<DataPoint>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    List<DataPoint> added = List.copyOf(change.getAddedSubList());
                    Platform.runLater(() ->{
                        for (DataPoint point : added) {
                            Duration duration = Duration.between(fileManager.BaseTime,point.timestamp());
                            double days = duration.toDays();
                            series.getData().add(new XYChart.Data<>(days,point.value()));
                        }
                    });
                }
            }
        });
    }

    public XYChart.Series<Number,Number> populateserie() throws IOException {
        List<List<String>> values = fileManager.readCsvData();
        if(values.isEmpty()) throw new IOException("No data found");
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        int sensors = (values.getFirst().size()) / 3;
        for(List<String> point : values){
            for (int i = 0; i < sensors; i++) {
                int timeIdx = i * 3 + 1;
                int volumeIdx = i * 3 + 2;
                String date = point.get(timeIdx);
                String volume = point.get(volumeIdx);
                if (date == null || date.isEmpty() || volume == null || volume.isEmpty()) continue;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd_MM_yy_HH_mm_ss");
                LocalDateTime time = LocalDateTime.parse(date,formatter);
                Duration duration = Duration.between(fileManager.BaseTime,time);
                double days = duration.toDays();
                series.getData().add(new XYChart.Data<>(days,Double.parseDouble(volume)));
            }
        }
        return series;
    }



}
