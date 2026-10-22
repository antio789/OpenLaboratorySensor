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
import java.util.Objects;

//TODO: rework for it to be able to take a csv file and be created multiple times.
//TODO: remove from main application state can be reused, just ensure that it can always operate in the background, as each experiemnt will have its own.
public class ExperimentFileManager {
    private final Path CSV;
    public final LocalDateTime BaseTime;
    private final List<Integer> resolutions;
    public ObservableList<DataPoint> points = FXCollections.observableArrayList();

    public ExperimentFileManager(String name, List<Integer> resolutions){
        this.resolutions = resolutions;
        Path outputDir = Paths.get(System.getProperty("user.home"), Ref.Name);
        CSV = outputDir.resolve(String.format("%s_Experiment_%s.csv", name, getTime()));
        BaseTime = LocalDateTime.now();

        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            List<String> header = new ArrayList<>(List.of("timestamp,value"));
            for (int i = 0; i < resolutions.size(); i++) {
                header.set(0, header.getFirst() + ",volumetric_resolution_" + (i + 1));
            }
            Files.write(CSV, header,StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            System.out.println("CSV written to: " + CSV.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public int getTotalResolution(){
        int total = 0;
        for (int resolution : resolutions) total += resolution;
        return total;
    }

    //returns the time in the desired format
    private String getTime(){
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd_MM_yy_HH_mm_ss");
        return date.format(formatter);
    }

//TODO: push files to own folder, ask first to create one if not present
    public String copy_CSV(){
        Path datacopy = Paths.get(System.getProperty("user.home"), "Documents").resolve(String.format("Datacopy_%s.csv",getTime()));
        try{
            Files.copy(CSV,datacopy, StandardCopyOption.REPLACE_EXISTING);
            return String.format("File copied at : %s", datacopy.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
            return "Failed to write CSV: " + e.getMessage();
        }
    }
    public List<List<String>> readCsvData() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(CSV)){
            return reader.lines()
                    .map(line -> Arrays.asList(line.split(",")))
                    .toList();
        }/*catch (IOException e){
            System.err.println("Failed to write CSV: " + e.getMessage());*/
    }

    public void writeStateToCSV(final String state){
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    if(Objects.equals(state, "true")) {
                        List<String> line = new ArrayList<>(List.of(getTime(), state));
                        for (int resolution : resolutions) line.add(String.valueOf(resolution));
                        Files.write(
                                CSV,
                                List.of(String.join(",", line)),
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND
                        );
                    } else {
                        String line = getTime() + "," + state;
                        Files.write(
                                CSV,
                                List.of(line),
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND
                        );
                    }
                } catch (IOException e) {
                    System.err.println("Failed to write CSV: " + e.getMessage());
                }
                if(Objects.equals(state, "true")){
                    final int value = (points.size() + 1) * getTotalResolution();
                    Platform.runLater(() -> {
                        points.add(new DataPoint(LocalDateTime.now(), value));
                    });
                }
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public void addFakeDataPoint() {
        final int value = (points.size() + 1) * getTotalResolution();
        Platform.runLater(() -> {
            points.add(new DataPoint(BaseTime.plusDays(points.size()), value));
        });
    }


}
