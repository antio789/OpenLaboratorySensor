package ant.gasmeter.BLE_Handler;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import javafx.concurrent.Task;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

public class BLEScanner {
    private DeviceManager deviceManager;
    private BluetoothAdapter adapter;
    private final Path BLE_DEVICES_CSV;

    public BLEScanner() {
        Path outputDir = Paths.get(System.getProperty("user.home"), "GasMeter");
        BLE_DEVICES_CSV = outputDir.resolve("ble_devices.csv");
        
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            if (!Files.exists(BLE_DEVICES_CSV)) {
                Files.write(BLE_DEVICES_CSV, List.of("name,address,uuid"), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize BLE devices CSV: " + e.getMessage());
        }
    }

    public void scanForDevices(TextField statusField) {
        Task<Void> scanTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    this.updateMessage("Initializing BLE scan...");
                    deviceManager = DeviceManager.createInstance(false);
                    adapter = deviceManager.getAdapters().getFirst();
                    deviceManager.setDefaultAdapter(adapter);
                    
                    if (!adapter.isPowered()) {
                        adapter.setPowered(true);
                        Thread.sleep(1000);
                    }
                    
                    this.updateMessage("Scanning for BLE devices...");
                    adapter.startDiscovery();
                    Thread.sleep(5000);
                    adapter.stopDiscovery();
                    
                    List<BluetoothDevice> devices = deviceManager.getDevices(false);
                    this.updateMessage("Found " + devices.size() + " devices");
                    
                    saveDevicesToCSV(devices);
                    this.updateMessage("Devices saved to CSV");
                    
                } catch (Exception e) {
                    this.updateMessage("Scan failed: " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }
        };
        
        statusField.textProperty().bind(scanTask.messageProperty());
        Thread thread = new Thread(scanTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void saveDevicesToCSV(List<BluetoothDevice> devices) {
        try {
            List<String> lines = devices.stream()
                    .filter(dev -> dev.getName() != null && !dev.getName().isEmpty())
                    .map(dev -> {
                        String name = dev.getName().replace(",", ";");
                        String address = dev.getAddress();
                        String uuid = dev.getUuid();
                        return String.format("%s,%s,%s", name, address, uuid);
                    })
                    .collect(Collectors.toList());
            
            if (!lines.isEmpty()) {
                Files.write(BLE_DEVICES_CSV, lines, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            System.err.println("Failed to save devices to CSV: " + e.getMessage());
        }
    }

    public List<String[]> loadDevicesFromCSV() {
        try {
            return Files.readAllLines(BLE_DEVICES_CSV, StandardCharsets.UTF_8).stream()
                    .skip(1)
                    .map(line -> line.split(","))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Failed to load devices from CSV: " + e.getMessage());
            return List.of();
        }
    }

    public void clearDevicesCSV() {
        try {
            Files.write(BLE_DEVICES_CSV, List.of("name,address,uuid"), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to clear devices CSV: " + e.getMessage());
        }
    }
}
