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

    //returns the data rows of the csv, header row skipped
    public List<List<String>> readCsvData() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvFilePath)){
            return reader.lines()
                    .skip(1)
                    .map(line -> Arrays.asList(line.split(",")))
                    .toList();
        }/*catch (IOException e){
            System.err.println("Failed to write CSV: " + e.getMessage());*/
    }

    public void writeCycleToCSV(final int sensor){
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                writeCycle(sensor);
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    //writes the cycles that happened while the app was disconnected, all at the reconnection time
    public void writeMissedCycles(final int sensor, final int missedCycles){
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i < missedCycles; i++) {
                    writeCycle(sensor);
                }
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public int getSensorCycleCount(int sensor){
        if (sensor < 1 || sensor > sensorCycleCounts.size()) return 0;
        return sensorCycleCounts.get(sensor - 1);
    }

    //writes one cycle row for the sensor; runs on a background thread
    private void writeCycle(final int sensor){
        if (sensor < 1 || sensor > sensorResolutions.size()) return;
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
    }

    //exports the event log as a wide CSV: one column block per sensor (count, time, cumulative volume),
    //one row per event, blanks for sensors that did not fire - easy to read and chart in a spreadsheet
    public Path getLogDirectory(){
        return csvFilePath.getParent();
    }

    public String getExportFileName(){
        return csvFilePath.getFileName().toString().replace("_Experiment_", "_Export_");
    }

    public String exportCsvFile(Path exportPath){
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
                if (record.size() < 4) continue; // skip torn or malformed rows
                int sensor;
                try {
                    sensor = Integer.parseInt(record.get(0));
                } catch (NumberFormatException e) {
                    continue;
                }
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
        } catch (Exception e) {
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
