package ant.gasmeter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordtoCSV {
    private final Path CSV;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Thread worker;

    private String getTime(){
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MM yy, k:m");
        String text = date.format(formatter);
        return LocalDateTime.parse(text, formatter).toString();
    }

    public RecordtoCSV(Path path) {
        CSV = path;
        worker = new Thread(this::processQueue);
        worker.setDaemon(true); // does not block app exit
        worker.start();
    }

    public void recordDataEntry(String value) {
        String line = getTime()+","+value;
        queue.add(line);
    }

    private void processQueue() {
        while (true) {
            try {
                // take() blocks until something is available
                String line = queue.take();
                Files.write(
                        CSV,
                        List.of(line),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                System.err.println("Failed to write CSV: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // stop thread gracefully
            }
        }
    }
}
