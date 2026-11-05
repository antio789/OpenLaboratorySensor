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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BLEScanner {
    private BluetoothAdapter adapter;
    private record DeviceData(String name, String[] uuids, String mac) {}

    public BLEScanner(TextField macField) {
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
                Platform.runLater(() ->  {
                    macField.setText(deviceMap.get(selectedDevice).mac);
                });
            }
            stage.close();
        });
        Task<Void> scanTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    DeviceManager deviceManager = DeviceManager.createInstance(false);
                    adapter = deviceManager.getAdapters().getFirst();
                    deviceManager.setDefaultAdapter(adapter);

                    if (!adapter.isPowered()) {
                        adapter.setPowered(true);
                        Thread.sleep(1000);
                    }
                    adapter.startDiscovery();
                    while (!isCancelled()) {
                        List<BluetoothDevice> discovered = deviceManager.getDevices(false);
                        // snapshot the device data on this thread: BlueZ can null out properties mid-discovery,
                        // so the UI thread must not read from the live wrappers
                        List<DeviceData> scanning = discovered.stream()
                                .filter(dev -> dev.getName() != null && dev.getUuids() != null && dev.getUuids().length > 0)
                                .map(dev -> new DeviceData(dev.getName(), dev.getUuids().clone(), dev.getAddress()))
                                .toList();
                        if (!scanning.isEmpty()) {
                            Platform.runLater(() -> {
                                for (DeviceData device : scanning) {
                                    String deviceDisplay = device.name() + " : " + Arrays.toString(device.uuids());
                                    if (!deviceItems.contains(deviceDisplay)) {
                                        deviceItems.add(deviceDisplay);
                                        deviceMap.put(deviceDisplay, device);
                                    }
                                }
                            });
                        }
                    }
                } catch (Exception e) {
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

        Thread thread = new Thread(scanTask);
        thread.setDaemon(true);
        thread.start();
    }
}
