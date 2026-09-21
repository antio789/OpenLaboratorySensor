package ant.gasmeter.utils;

import ant.gasmeter.DataManagement;
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

public class ChartHandler {

    public static void setupChart(DataManagement data, LineChart<Number, Number> lineChart, Pane chartPane) {
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

    private void refreshChart(LineChart<Number, Number> lineChart,DataManagement DATA){
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

}
