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
        int gasflow = 0;
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for(List<String> point : values){
            String date = point.getFirst();
            String value = point.get(1);
            if(value.equalsIgnoreCase( "true")){
                int resolution = 0;
                for (int i = 2; i < point.size(); i++) {
                    try {
                        resolution += Integer.parseInt(point.get(i));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (resolution == 0) resolution = fileManager.getTotalResolution();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MM yy, k:m");
                LocalDateTime time = LocalDateTime.parse(date,formatter);
                Duration duration = Duration.between(fileManager.BaseTime,time);
                float days = duration.toDays();
                gasflow += resolution;
                series.getData().add(new XYChart.Data<>(days,gasflow));
            }
        }
        return series;
    }



}
