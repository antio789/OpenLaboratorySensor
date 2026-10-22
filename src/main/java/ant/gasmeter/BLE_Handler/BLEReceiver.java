package ant.gasmeter.BLE_Handler;

import ant.gasmeter.utils.ExperimentFileManager;
import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import javafx.concurrent.Task;
import javafx.scene.control.TextField;
import org.freedesktop.dbus.exceptions.DBusException;

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


    String ARDUINO_SERVICE_UUID = "a117480e-14a0-482e-b417-629d8829a1c0";
    String SENSOR_CYCLE_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a0";
    String SENSOR_TOTAL_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a1";
    String SENSOR_WARNING_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a2";
    String SENSOR_CONFIG_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a4";

    Map<Integer, String> warningMessages = Map.of(
            1, "SEQUENCE VIOLATION: S2 switched before S1",
            2, "SEQUENCE VIOLATION: S1 receded before S2",
            3, "Warning: Hovering detected on S2",
            4, "Warning: Liquid dropped fast or power cut, both disconnected simultaneously"
    );


    public BLEReceiver(ExperimentFileManager _data){
        DATA = _data;
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
                    this.updateMessage("All fields valid, connecting to device");

                    sensor = findDevice(address);
                    if (sensor != null) {
                        setupGattNotification(sensor);
                        return true;
                    }else{
                        this.updateMessage("device not found");
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
                        updateMessage("device found");
                        if (dev.connect()) {
                            updateMessage("connected");
                        } else {
                            updateMessage("connection failed");
                        }
                        return dev;
                    }
                }
                updateMessage("device not found");
                return null;
            }

            private void setupGattNotification(BluetoothDevice sensor) throws DBusException {
                try{
                    Thread.sleep(500);//ensure proper connection before refreshing GATT
                }catch(InterruptedException ex) {
                    updateMessage("Bluetooth error: " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
                BluetoothGattService Service = sensor.getGattServiceByUuid(ARDUINO_SERVICE_UUID);
                Service.refreshGattCharacteristics();

                BluetoothGattCharacteristic Config_Gatt = Service.getGattCharacteristicByUuid(SENSOR_CONFIG_UUID);
                if (Config_Gatt != null) {
                    Config_Gatt.writeValue(DATA.getPinsConfig().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    updateMessage("sensor config sent");
                }

                BluetoothGattCharacteristic Cycle_Gatt = Service.getGattCharacteristicByUuid(SENSOR_CYCLE_UUID);

                try {
                    Cycle_Gatt.startNotify();
                } catch (Exception ex) {
                    updateMessage("Bluetooth error: " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
                deviceManager.registerPropertyHandler(new GATT_Cycle_NotificationHandler(Cycle_Gatt.getDbusPath(),DATA));
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
