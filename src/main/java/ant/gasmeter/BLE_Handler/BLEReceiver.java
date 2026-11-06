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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
//TODO: send warning messages.
//TODO: save Total values
public class BLEReceiver {
    BluetoothAdapter BTadapter;
    DeviceManager deviceManager;
    BluetoothDevice sensor;
    GATT_Cycle_NotificationHandler cycleHandler;
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

    public void initializeBluetoothConnection(TextField BTstatus, String address){
        Task attemptConnection = new Task<>() {

            private void status(String message){
                this.updateMessage(message);
                if (logger != null) logger.log(message);
            }


            @Override
            protected Boolean call() {
                try{
                    DeviceManager.createInstance(false);
                    deviceManager = DeviceManager.getInstance();
                    BTadapter = deviceManager.getAdapters().getFirst();
                    deviceManager.setDefaultAdapter(BTadapter);
                    status("All fields valid, connecting to device");

                    // try a few times before giving up
                    int maxAttempts = 3;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        sensor = findDevice(address);
                        if (sensor != null) break;
                        if (attempt < maxAttempts) {
                            status("device not found, retrying (attempt " + (attempt + 1) + " of " + maxAttempts + ")");
                            Thread.sleep(2000);
                        }
                    }
                    if (sensor == null) {
                        status("device not found, connection failed");
                        return false;
                    }

                    setupGattNotification(sensor);
                    startConnectionMonitor(address);
                    return true;
                } catch (Exception e) {
                    status("Bluetooth error: " + e.getMessage());
                    return false;
                }
            }
        };
        BTstatus.textProperty().bind(attemptConnection.messageProperty());
        Thread thread = new Thread(attemptConnection);
        thread.setDaemon(true);
        thread.start();
    }

    private BluetoothDevice findDevice(String mac){
        List<BluetoothDevice> devices = deviceManager.getDevices(false);
        for (BluetoothDevice dev : devices) {
            if (dev.getAddress().equalsIgnoreCase(mac)) {
                if (logger != null) logger.log("device found, connecting");
                if (dev.connect()) {
                    if (logger != null) logger.log("connected");
                    return dev;
                }
                if (logger != null) logger.log("ERROR: connection to device failed");
                return null;
            }
        }
        return null;
    }

    private void setupGattNotification(BluetoothDevice sensor) throws DBusException, InterruptedException {
        try{
            Thread.sleep(500);//ensure proper connection before refreshing GATT
        }catch(InterruptedException ex) {
            if (logger != null) logger.log("Bluetooth error: " + ex.getMessage());
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
            if (logger != null) logger.log("ERROR: sensor config characteristic not found on device");
        } else {
            try {
                // "request" = acknowledged write, so the sketch is guaranteed to receive the full config
                Config_Gatt.writeValue(
                        DATA.getSensorPinsConfig().getBytes(StandardCharsets.UTF_8),
                        Map.of("type", "request"));
                if (logger != null) logger.log("sensor config sent");
            } catch (Exception ex) {
                if (logger != null) logger.log("Bluetooth error sending sensor config: " + ex.getMessage());
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
            if (logger != null) logger.log("ERROR: cycle characteristic not found on device");
            return;
        }
        try {
            Cycle_Gatt.startNotify();
        } catch (Exception ex) {
            if (logger != null) logger.log("Bluetooth error: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
        // avoid duplicate handlers when reconnecting
        if (cycleHandler != null) {
            deviceManager.unregisterPropertyHandler(cycleHandler);
        }
        cycleHandler = new GATT_Cycle_NotificationHandler(Cycle_Gatt.getDbusPath(),DATA,logger);
        deviceManager.registerPropertyHandler(cycleHandler);

        checkTotals(Service);
    }

    //compares the arduino's per-sensor cycle totals with the app's counts and reconciles the difference;
    //the arduino keeps counting while disconnected, so a difference means cycles were missed during a dropout
    private void checkTotals(BluetoothGattService service){
        try {
            BluetoothGattCharacteristic totalGatt = service.getGattCharacteristicByUuid(SENSOR_TOTAL_UUID);
            if (totalGatt == null) {
                if (logger != null) logger.log("ERROR: sensor total characteristic not found on device");
                return;
            }
            byte[] raw = totalGatt.readValue();
            if (raw == null || raw.length < DATA.getSensorCount() * 4) {
                if (logger != null) logger.log("ERROR: could not read sensor totals from device");
                return;
            }
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            for (int sensor = 1; sensor <= DATA.getSensorCount(); sensor++) {
                int arduinoCount = buffer.getInt((sensor - 1) * 4);
                int appCount = DATA.getSensorCycleCount(sensor);
                int missed = arduinoCount - appCount;
                if (logger == null) continue;
                if (missed > 0) {
                    logger.log("WARNING: sensor " + sensor + " counted " + missed
                            + " cycle(s) while the app was disconnected, adding them to the log at reconnection time");
                    DATA.writeMissedCycles(sensor, missed);
                } else if (missed < 0) {
                    logger.log("WARNING: arduino reports " + (-missed)
                            + " fewer cycle(s) than the app for sensor " + sensor + " (board rebooted?)");
                }
            }
        } catch (Exception ex) {
            if (logger != null) logger.log("Bluetooth error reading sensor totals: " + ex.getMessage());
        }
    }

    //watches the connection and tries to reconnect when it drops
    private void startConnectionMonitor(String address){
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
                if (sensor == null || sensor.isConnected()) continue;
                if (logger != null) logger.log("connection lost, attempting to reconnect");
                try {
                    BluetoothDevice dev = deviceManager.getDevices(false).stream()
                            .filter(d -> d.getAddress().equalsIgnoreCase(address))
                            .findFirst().orElse(null);
                    if (dev != null && dev.connect()) {
                        sensor = dev;
                        setupGattNotification(dev);
                        if (logger != null) logger.log("reconnected and sensor config resent");
                    } else {
                        if (logger != null) logger.log("reconnect attempt failed, will retry");
                    }
                } catch (Exception ex) {
                    if (logger != null) logger.log("reconnect attempt failed: " + ex.getMessage());
                }
                // give the device time to reappear before the next attempt
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }

    public void closeConnection() {
        if (sensor != null) {
            sensor.disconnect();
        }
    }

}
