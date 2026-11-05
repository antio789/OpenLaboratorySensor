package ant.gasmeter.utils;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//TODO: remove from main application state can be reused, just ensure that it can always operate in the background, as each experiemnt will have its own.
public class ExperimentFileManager {
    private final Path csvFilePath;
    public final LocalDateTime experimentStartTime;
    private final List<Double> sensorResolutions;
    private final List<String> lowerSensorPins;
    private final List<String> upperSensorPins;
    public ObservableList<DataPoint> dataPoints = FXCollections.observableArrayList();
    private final List<Integer> sensorCycleCounts;
    private final List<Double> sensorCumulativeVolumes;

    public ExperimentFileManager(String name, List<Double> sensorResolutions, List<String> lowerSensorPins, List<String> upperSensorPins){
        this.sensorResolutions = sensorResolutions;
        this.sensorCycleCounts = new ArrayList<>();
        this.sensorCumulativeVolumes = new ArrayList<>();
        for (int i = 0; i < sensorResolutions.size(); i++) {
            this.sensorCycleCounts.add(0);
            this.sensorCumulativeVolumes.add(0.0);
        }
        this.lowerSensorPins = lowerSensorPins;
        this.upperSensorPins = upperSensorPins;
        Path outputDir = Paths.get(System.getProperty("user.home"), Ref.Name);
        csvFilePath = outputDir.resolve(String.format("%s_Experiment_%s.csv", name, getTime()));
        experimentStartTime = LocalDateTime.now();

        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            // event-log format: one self-contained row per completed cycle, no empty cells
            Files.write(csvFilePath, List.of("sensor,count,time,volume"), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            System.out.println("CSV written to: " + csvFilePath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public int getSensorCount(){
        return sensorResolutions.size();
    }

    public double getSensorResolution(int sensor){
        if (sensor < 1 || sensor > sensorResolutions.size()) return 0;
        return sensorResolutions.get(sensor - 1);
    }

    //returns the time in the desired format
    private String getTime(){
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd_MM_yy_HH_mm_ss");
        return date.format(formatter);
    }

//TODO: push files to own folder, ask first to create one if not present
    public String copyCsvFile(){
        Path datacopy = Paths.get(System.getProperty("user.home"), "Documents").resolve(String.format("Datacopy_%s.csv",getTime()));
        try{
            Files.copy(csvFilePath,datacopy, StandardCopyOption.REPLACE_EXISTING);
            return String.format("File copied at : %s", datacopy.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
            return "Failed to write CSV: " + e.getMessage();
        }
    }

    //returns the data rows of the csv, header row skipped
    public List<List<String>> readCsvData() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvFilePath)){
            return reader.lines()
                    .skip(1)
                    .map(line -> Arrays.asList(line.split(",")))
                    .toList();
        }
    }

    public void writeCycleToCSV(final int sensor){
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (sensor < 1 || sensor > sensorResolutions.size()) return null;
                sensorCycleCounts.set(sensor - 1, sensorCycleCounts.get(sensor - 1) + 1);
                sensorCumulativeVolumes.set(sensor - 1, sensorCumulativeVolumes.get(sensor - 1) + getSensorResolution(sensor));
                try {
                    String line = sensor + "," + sensorCycleCounts.get(sensor - 1) + "," + getTime() + "," + sensorCumulativeVolumes.get(sensor - 1);
                    Files.write(csvFilePath, List.of(line), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    System.err.println("Failed to write CSV: " + e.getMessage());
                }
                final double value = sensorCumulativeVolumes.get(sensor - 1);
                Platform.runLater(() -> {
                    dataPoints.add(new DataPoint(LocalDateTime.now(), sensor, value));
                });
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    //exports the event log as a wide CSV: one column block per sensor (count, time, cumulative volume),
    //one row per event, blanks for sensors that did not fire - easy to read and chart in a spreadsheet
    public String exportCsvFile(){
        String fileName = csvFilePath.getFileName().toString().replace("_Experiment_", "_Export_");
        Path exportPath = csvFilePath.getParent().resolve(fileName);
        try {
            List<List<String>> records = readCsvData();
            List<String> lines = new ArrayList<>();
            List<String> headerCells = new ArrayList<>();
            for (int i = 1; i <= getSensorCount(); i++) {
                headerCells.add("sensor" + i + "_count");
                headerCells.add("sensor" + i + "_time");
                headerCells.add("sensor" + i + "_cumulative_volume");
            }
            lines.add(String.join(",", headerCells));

            for (List<String> record : records) {
                int sensor = Integer.parseInt(record.get(0));
                List<String> cells = new ArrayList<>();
                for (int i = 1; i <= getSensorCount(); i++) {
                    if (i == sensor) {
                        cells.add(record.get(1));  // count
                        cells.add(record.get(2));  // time
                        cells.add(record.get(3));  // cumulative volume
                    } else {
                        cells.add("");
                        cells.add("");
                        cells.add("");
                    }
                }
                lines.add(String.join(",", cells));
            }
            Files.write(exportPath, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return String.format("Exported to: %s", exportPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
            return "Failed to write CSV: " + e.getMessage();
        }
    }

    public String getSensorPinsConfig(){
        StringBuilder cfg = new StringBuilder();
        for (int i = 0; i < lowerSensorPins.size(); i++) {
            if (i > 0) cfg.append(";");
            cfg.append(lowerSensorPins.get(i)).append(",").append(upperSensorPins.get(i));
        }
        return cfg.toString();
    }

    public void addFakeDataPoint() {
        int sensor = (dataPoints.size() % sensorResolutions.size()) + 1;
        sensorCumulativeVolumes.set(sensor - 1, sensorCumulativeVolumes.get(sensor - 1) + getSensorResolution(sensor));
        final double value = sensorCumulativeVolumes.get(sensor - 1);
        Platform.runLater(() -> {
            dataPoints.add(new DataPoint(experimentStartTime.plusDays(dataPoints.size()), sensor, value));
        });
    }


}
