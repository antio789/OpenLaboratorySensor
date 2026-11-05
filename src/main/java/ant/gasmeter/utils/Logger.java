package ant.gasmeter.utils;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

//per-experiment logger: shows timestamped messages in a scrollable text area
//and appends them to a txt file next to the experiment csv
public class Logger {
    private final Path logFilePath;
    private final TextArea textArea;

    public Logger(String experimentName, TextArea area){
        textArea = area;
        Path outputDir = Paths.get(System.getProperty("user.home"), Ref.Name);
        logFilePath = outputDir.resolve(String.format("%s_Log_%s.txt", experimentName, getTime()));
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            Files.write(logFilePath, List.of("Log for experiment: " + experimentName), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.err.println("Failed to create log file: " + e.getMessage());
        }
    }

    public void log(String message){
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd_MM_yy HH:mm:ss")) + "] " + message;
        try {
            Files.write(logFilePath, List.of(line), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write log file: " + e.getMessage());
        }
        if (textArea != null) {
            Platform.runLater(() -> textArea.appendText(line + System.lineSeparator()));
        }
    }

    private String getTime(){
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd_MM_yy_HH_mm_ss"));
    }
}
