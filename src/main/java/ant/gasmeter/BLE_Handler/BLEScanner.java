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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;

public class BLEScanner {
    private BluetoothAdapter adapter;

    //TODO: show different devices and propose to select instead of dumping everything
    public BLEScanner() {
    }

    public void scanForDevices(TextField statusField) {
        ObservableList<BluetoothDevice> deviceItems = FXCollections.observableArrayList();
        ListView<BluetoothDevice> deviceListView = createDeviceListView(deviceItems);
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
            BluetoothDevice selectedDevice = deviceListView.getSelectionModel().getSelectedItem();
            if (selectedDevice == null) return;
            stage.close();
        });
        Task<List<BluetoothDevice>> scanTask = new Task<>() {
            @Override
            protected List<BluetoothDevice> call(){
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
                    while(!isCancelled()){
                        List<BluetoothDevice> discovered = deviceManager.getDevices(false);
                        List<BluetoothDevice> scanning = discovered.stream().filter(dev -> dev.getName()!=null && dev.getUuids().length>0).toList();
                        if(!scanning.isEmpty()){
                            Platform.runLater(() -> {
                                for (BluetoothDevice device : scanning) {
                                    if(!deviceItems.contains(device)) deviceItems.add(device);
                                }
                            });
                        }
                    }
                    List<BluetoothDevice> devices = deviceManager.getDevices(false);
                    this.updateMessage("Found " + devices.size() + " devices");
                    this.updateMessage("Devices saved to CSV");
                    return devices;
                    
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
                } catch (Exception ignored) {}
            }
        };
        stopButton.setOnAction(event -> {
            scanTask.cancel();
            stage.close();
        });
        statusField.textProperty().bind(scanTask.messageProperty());

        scanTask.setOnSucceeded(event -> {
            List<BluetoothDevice> devices = scanTask.getValue();
            
            statusField.textProperty().unbind();
            statusField.setText("Devices saved to CSV");
        });
        Thread thread = new Thread(scanTask);
        thread.setDaemon(true);
        thread.start();
    }

    private static ListView<BluetoothDevice> createDeviceListView(ObservableList<BluetoothDevice> deviceItems) {
        ListView<BluetoothDevice> deviceListView = new ListView<>(deviceItems);
        deviceListView.setPrefSize(350, 200);
        deviceListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(BluetoothDevice device, boolean empty) {
                super.updateItem(device, empty);
                if (empty || device == null) setText(null);
                else setText(device.getName()+ " : " + Arrays.toString(device.getUuids()));
            }
        });
        return deviceListView;
    }
}
