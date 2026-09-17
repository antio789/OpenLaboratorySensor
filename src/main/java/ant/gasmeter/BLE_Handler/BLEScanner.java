package ant.gasmeter.BLE_Handler;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;

public class BLEScanner {
    private BluetoothAdapter adapter;
    record DeviceData(String name, String[] uuids) {}

    //TODO: show different devices and propose to select instead of dumping everything
    public BLEScanner() {
    }

    public void scanForDevices(TextField statusField) {
        Map<String, DeviceData> deviceMap = new HashMap<>();
        ObservableList<String> deviceItems = FXCollections.observableArrayList();
        ListView<String> deviceListView = new ListView<>(deviceItems);
        deviceListView.setPrefSize(350,450);

        Button selectButton = new Button("Select");
        Button stopButton = new Button("Cancel");

        VBox layout = new VBox(10, deviceListView, new HBox(10, selectButton, stopButton));
        layout.setPadding(new Insets(15));
        Stage stage = new Stage();
        stage.setTitle("Select BLE Device");
        stage.setScene(new Scene(layout, 400, 300));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();

        selectButton.setOnAction(_ -> {
            String selectedDevice = deviceListView.getSelectionModel().getSelectedItem();
            if (selectedDevice != null){
                exportData(deviceMap.get(selectedDevice));
            }
            stage.close();
        });
        Task<Void> scanTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    this.updateMessage("Initializing BLE scan...");
                    DeviceManager deviceManager = DeviceManager.createInstance(false);
                    adapter = deviceManager.getAdapters().getFirst();
                    deviceManager.setDefaultAdapter(adapter);

                    if (!adapter.isPowered()) {
                        adapter.setPowered(true);
                        Thread.sleep(1000);
                    }
                    adapter.startDiscovery();
                    this.updateMessage("Scanning for BLE devices...");
                    while (!isCancelled()) {
                        List<BluetoothDevice> discovered = deviceManager.getDevices(false);
                        List<BluetoothDevice> scanning = discovered.stream().filter(dev -> dev.getName() != null && dev.getUuids() != null && dev.getUuids().length > 0).toList();
                        if (!scanning.isEmpty()) {
                            Platform.runLater(() -> {
                                for (BluetoothDevice device : scanning) {
                                    String deviceDisplay = device.getName() + " : " + Arrays.toString(device.getUuids());
                                    if (!deviceItems.contains(deviceDisplay)) {
                                        deviceItems.add(deviceDisplay);
                                        deviceMap.put(deviceDisplay, new DeviceData(device.getName(), device.getUuids().clone()));
                                    }
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    this.updateMessage("Scan failed: " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void cancelled() {
                try {
                    adapter.stopDiscovery();
                } catch (Exception ignored) {
                }
            }
        };
        stopButton.setOnAction(_ -> {
            scanTask.cancel();
            stage.close();
        });
        statusField.textProperty().bind(scanTask.messageProperty());

        Thread thread = new Thread(scanTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void exportData(DeviceData data){

    }
}
