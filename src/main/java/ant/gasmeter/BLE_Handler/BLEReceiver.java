package ant.gasmeter.BLE_Handler;

import ant.gasmeter.utils.ExperimentFileManager;
import ant.gasmeter.utils.Logger;
import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import javafx.concurrent.Task;
import javafx.scene.control.TextField;
import org.freedesktop.dbus.exceptions.DBusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
//TODO: create log file
//TODO: have visual textfield for logfile
//TODO: send warning messages.
//TODO: save Total values
//TODO: fix controller for new experiment
//TODO: Rework GATT cycle to work with updated code
public class BLEReceiver {
    BluetoothAdapter BTadapter;
    DeviceManager deviceManager;
    BluetoothDevice sensor;
    ExperimentFileManager DATA;
    Logger logger;


    String ARDUINO_SERVICE_UUID = "a117480e-14a0-482e-b417-629d8829a1c0";
    String SENSOR_CYCLE_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a0";
    String SENSOR_TOTAL_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a1";
    String SENSOR_WARNING_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a2";
    String SENSOR_CONFIG_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a4";

    Map<Integer, String> warningMessages = Map.of(
            1, "SEQUENCE VIOLATION: S2 switched before S1",
            2, "SEQUENCE VIOLATION: S1 receded before S2",
            3, "Warning: Hovering detected on S2",
            4, "Warning: Liquid dropped fast or power cut, both disconnected simultaneously",
            5, "ERROR: Invalid sensor pin config received by the Arduino",
            6, "ERROR: BLE module failed to start at boot (recovered)"
    );


    public BLEReceiver(ExperimentFileManager _data, Logger _logger){
        DATA = _data;
        logger = _logger;
    }

    //updates the connection status message and logs it
    private void status(String message){
        updateMessage(message);
        if (logger != null) logger.log(message);
    }

    // TODO: Right now doesnt catch errors, find a way to transfer a failed connection attempt
    public void initializeBluetoothConnection(TextField BTstatus, String address){
        Task attemptConnection = new Task<>() {

            @Override
            protected Boolean call() {
                try{
                    DeviceManager.createInstance(false);
                    deviceManager = DeviceManager.getInstance();
                    BTadapter = deviceManager.getAdapters().getFirst();
                    deviceManager.setDefaultAdapter(BTadapter);
                    status("All fields valid, connecting to device");

                    sensor = findDevice(address);
                    if (sensor != null) {
                        setupGattNotification(sensor);
                        return true;
                    }else{
                        this.status("device not found");
                        return false;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            private BluetoothDevice findDevice(String mac){
                List<BluetoothDevice> devices = deviceManager.getDevices(false);
                for (BluetoothDevice dev : devices) {
                    if (dev.getAddress().equalsIgnoreCase(mac)) {
                        status("device found");
                        if (dev.connect()) {
                            status("connected");
                        } else {
                            status("connection failed");
                        }
                        return dev;
                    }
                }
                status("device not found");
                return null;
            }

            private void setupGattNotification(BluetoothDevice sensor) throws DBusException, InterruptedException {
                try{
                    Thread.sleep(500);//ensure proper connection before refreshing GATT
                }catch(InterruptedException ex) {
                    status("Bluetooth error: " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
                BluetoothGattService Service = sensor.getGattServiceByUuid(ARDUINO_SERVICE_UUID);
                Service.refreshGattCharacteristics();

                // on a first connection BlueZ may still be discovering the characteristics,
                // so retry the lookup instead of silently skipping the config write
                BluetoothGattCharacteristic Config_Gatt = null;
                for (int attempt = 0; attempt < 5 && Config_Gatt == null; attempt++) {
                    Config_Gatt = Service.getGattCharacteristicByUuid(SENSOR_CONFIG_UUID);
                    if (Config_Gatt == null) {
                        Service.refreshGattCharacteristics();
                        Thread.sleep(500);
                    }
                }
                if (Config_Gatt == null) {
                    status("sensor config characteristic not found on device");
                } else {
                    try {
                        // "request" = acknowledged write, so the sketch is guaranteed to receive the full config
                        Config_Gatt.writeValue(
                                DATA.getSensorPinsConfig().getBytes(StandardCharsets.UTF_8),
                                Map.of("type", "request"));
                        status("sensor config sent");
                    } catch (Exception ex) {
                        status("Bluetooth error sending sensor config: " + ex.getMessage());
                    }
                }

                BluetoothGattCharacteristic Cycle_Gatt = null;
                for (int attempt = 0; attempt < 5 && Cycle_Gatt == null; attempt++) {
                    Cycle_Gatt = Service.getGattCharacteristicByUuid(SENSOR_CYCLE_UUID);
                    if (Cycle_Gatt == null) {
                        Service.refreshGattCharacteristics();
                        Thread.sleep(500);
                    }
                }

                if (Cycle_Gatt == null) {
                    status("cycle characteristic not found on device");
                    return;
                }
                try {
                    Cycle_Gatt.startNotify();
                } catch (Exception ex) {
                    status("Bluetooth error: " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
                deviceManager.registerPropertyHandler(new GATT_Cycle_NotificationHandler(Cycle_Gatt.getDbusPath(),DATA,logger));
            }
        };
        BTstatus.textProperty().bind(attemptConnection.messageProperty());
        Thread thread = new Thread(attemptConnection);
        thread.setDaemon(true);
        thread.start();
    }
    public void closeConnection() {
        if (sensor != null) {
            sensor.disconnect();
        }
    }

}
