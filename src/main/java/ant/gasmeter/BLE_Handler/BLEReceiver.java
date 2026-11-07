package ant.gasmeter.BLE_Handler;

import ant.gasmeter.utils.ExperimentFileManager;
import ant.gasmeter.utils.Logger;
import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import org.freedesktop.dbus.exceptions.DBusException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class BLEReceiver {
    BluetoothAdapter btAdapter;
    DeviceManager deviceManager;
    BluetoothDevice sensor;
    GATT_Cycle_NotificationHandler cycleHandler;
    GATT_Warning_NotificationHandler warningHandler;
    BluetoothGattService gattService;
    boolean deviceResetDone;
    private volatile boolean monitorActive = false;
    private Thread monitorThread;
    private TextField btStatusField;
    private Timeline statusAnimation;
    private int dotCount;
    ExperimentFileManager DATA;
    Logger logger;

    String ARDUINO_SERVICE_UUID = "a117480e-14a0-482e-b417-629d8829a1c0";
    String SENSOR_CYCLE_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a0";
    String SENSOR_TOTAL_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a1";
    String SENSOR_WARNING_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a3"; // matches warningNotify in the sketch
    String SENSOR_CONFIG_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a4";
    String SENSOR_RESET_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a5";

    static final Map<Integer, String> warningMessages = Map.of(
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

    public void initializeBluetoothConnection(TextField btStatus, String address){
        btStatusField = btStatus;
        btStatus.setEditable(false);
        showConnecting();
        Task attemptConnection = new Task<>() {

            private void status(String message){
                if (logger != null) logger.log(message);
            }


            @Override
            protected Boolean call() {
                try{
                    DeviceManager.createInstance(false);
                    deviceManager = DeviceManager.getInstance();
                    btAdapter = deviceManager.getAdapters().getFirst();
                    deviceManager.setDefaultAdapter(btAdapter);
                    status("connecting");

                    // the status field shows only the outcome, details go to the log.
                    // retry the whole connect + gatt setup as a unit: on a fresh connection
                    // the service may not be discovered yet, a retry usually succeeds
                    int maxAttempts = 3;
                    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        sensor = findDevice(address);
                        if (sensor == null) {
                            if (attempt < maxAttempts) {
                                if (logger != null) logger.log("device not found, retrying (attempt " + (attempt + 1) + " of " + maxAttempts + ")");
                                Thread.sleep(2000);
                            }
                            continue;
                        }
                        if (setupGattNotification(sensor)) {
                            startConnectionMonitor(address);
                            status("connected: experiment running");
                            showConnected();
                            return true;
                        }
                        // setup failed, disconnect and try the whole connection again
                        sensor.disconnect();
                        sensor = null;
                        if (attempt < maxAttempts) {
                            if (logger != null) logger.log("gatt setup failed, retrying connection (attempt " + (attempt + 1) + " of " + maxAttempts + ")");
                            Thread.sleep(2000);
                        }
                    }
                    status("connection failed, check the log");
                    showFailed();
                    return false;
                } catch (Exception e) {
                    if (logger != null) logger.log("Bluetooth error: " + e.getMessage());
                    status("connection failed, check the log");
                    showFailed();
                    return false;
                }
            }
        };
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

    private boolean setupGattNotification(BluetoothDevice sensor) throws DBusException, InterruptedException {
        try{
            Thread.sleep(500);//ensure proper connection before refreshing GATT
        }catch(InterruptedException ex) {
            if (logger != null) logger.log("Bluetooth error: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
        // after a (re)connect BlueZ resolves the services asynchronously; the wrapper caches the first
        // (possibly empty) query result, so we must use refreshGattServices() to force a real re-query
        BluetoothGattService Service = null;
        for (int attempt = 0; attempt < 10 && Service == null; attempt++) {
            sensor.refreshGattServices(); // re-queries BlueZ, bypassing the wrapper's discovery latch
            Service = sensor.getGattServiceByUuid(ARDUINO_SERVICE_UUID);
            if (Service == null) {
                Thread.sleep(500);
            }
        }
        if (Service == null) {
            if (logger != null) logger.log("ERROR: sensor service not found on device");
            return false;
        }
        gattService = Service;
        Service.refreshGattCharacteristics();

        // a new experiment starts from zero: reset the board totals once per connection session,
        // so stale counts from a previous experiment do not trigger missed-cycle warnings
        if (!deviceResetDone) {
            resetDevice();
            deviceResetDone = true;
        }

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
            return false;
        }
        try {
            Cycle_Gatt.startNotify();
        } catch (Exception ex) {
            if (logger != null) logger.log("Bluetooth error: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
        // avoid duplicate handlers when reconnecting
        if (cycleHandler != null) {
            deviceManager.unRegisterPropertyHandler(cycleHandler);
        }
        cycleHandler = new GATT_Cycle_NotificationHandler(Cycle_Gatt.getDbusPath(),DATA,logger);
        deviceManager.registerPropertyHandler(cycleHandler);

        // subscribe to the arduino warning codes as well
        BluetoothGattCharacteristic Warning_Gatt = Service.getGattCharacteristicByUuid(SENSOR_WARNING_UUID);
        if (Warning_Gatt == null) {
            if (logger != null) logger.log("ERROR: warning characteristic not found on device");
        } else {
            try {
                Warning_Gatt.startNotify();
                if (warningHandler != null) {
                    deviceManager.unRegisterPropertyHandler(warningHandler);
                }
                warningHandler = new GATT_Warning_NotificationHandler(Warning_Gatt.getDbusPath(), logger);
                deviceManager.registerPropertyHandler(warningHandler);
            } catch (Exception ex) {
                if (logger != null) logger.log("Bluetooth error subscribing to warnings: " + ex.getMessage());
            }
        }

        checkTotals(Service);
        return true;
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
            byte[] raw = totalGatt.readValue(Map.of());
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

    //watches the connection and tries to reconnect when it drops; stops when closeConnection is called
    private void startConnectionMonitor(String address){
        monitorActive = true;
        Thread monitor = new Thread(() -> {
            while (monitorActive) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
                if (!monitorActive) return;
                if (sensor == null || sensor.isConnected()) continue;
                if (logger != null) logger.log("connection lost, attempting to reconnect");
                try {
                    BluetoothDevice dev = deviceManager.getDevices(false).stream()
                            .filter(d -> d.getAddress() != null && d.getAddress().equalsIgnoreCase(address))
                            .findFirst().orElse(null);
                    if (dev != null && dev.connect()) {
                        if (setupGattNotification(dev)) {
                            sensor = dev;
                            if (logger != null) logger.log("reconnected and sensor config resent");
                        } else {
                            // tear the half-open connection down so the monitor actually retries
                            dev.disconnect();
                            if (logger != null) logger.log("reconnected but gatt setup failed, will retry");
                        }
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
        monitorThread = monitor;
        monitor.start();
    }

    //tells the arduino to zero all per-sensor totals and state machines
    public void resetDevice(){
        if (gattService == null) {
            if (logger != null) logger.log("ERROR: cannot reset device, not connected");
            return;
        }
        try {
            BluetoothGattCharacteristic resetGatt = gattService.getGattCharacteristicByUuid(SENSOR_RESET_UUID);
            if (resetGatt == null) {
                if (logger != null) logger.log("ERROR: reset characteristic not found on device");
                return;
            }
            resetGatt.writeValue(new byte[]{1}, Map.of("type", "request"));
            if (logger != null) logger.log("device totals reset");
        } catch (Exception ex) {
            if (logger != null) logger.log("Bluetooth error resetting device: " + ex.getMessage());
        }
    }

    //status field states: animated while connecting, colored final states.
    //UI updates are wrapped in Platform.runLater because these are called from background threads
    private void showConnecting(){
        Platform.runLater(() -> {
            if (btStatusField == null) return;
            btStatusField.setStyle("-fx-text-fill: #2980b9;");
            stopStatusAnimation();
            dotCount = 0;
            statusAnimation = new Timeline(new KeyFrame(Duration.millis(400), event -> {
                dotCount = (dotCount + 1) % 4;
                btStatusField.setText("connecting" + ".".repeat(dotCount));
            }));
            statusAnimation.setCycleCount(Timeline.INDEFINITE);
            statusAnimation.play();
        });
    }

    private void showConnected(){
        Platform.runLater(() -> {
            if (btStatusField == null) return;
            stopStatusAnimation();
            btStatusField.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            btStatusField.setText("connected: experiment running");
        });
    }

    private void showFailed(){
        Platform.runLater(() -> {
            if (btStatusField == null) return;
            stopStatusAnimation();
            btStatusField.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
            btStatusField.setText("connection failed, check the log");
        });
    }

    private void showDisconnected(){
        Platform.runLater(() -> {
            if (btStatusField == null) return;
            stopStatusAnimation();
            btStatusField.setStyle("-fx-text-fill: #7f8c8d;");
            btStatusField.setText("bluetooth connection closed");
        });
    }

    private void stopStatusAnimation(){
        if (statusAnimation != null) {
            statusAnimation.stop();
            statusAnimation = null;
        }
    }

    public boolean isConnected(){
        return sensor != null && sensor.isConnected();
    }

    public void closeConnection() {
        monitorActive = false; // stop the reconnect monitor
        showDisconnected();
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        if (sensor != null) {
            sensor.disconnect();
        }
    }

}
