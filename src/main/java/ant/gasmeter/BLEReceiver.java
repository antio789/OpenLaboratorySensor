package ant.gasmeter;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import javafx.concurrent.Task;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.List;

public class BLEReceiver {
    BluetoothAdapter BTadapter;
    DeviceManager deviceManager;
    BluetoothDevice sensor;

    String ARDUINO_BLE_ADDRESS = "f4:12:fa:6f:85:c5";
    String ARDUINO_SERVICE_UUID = "a117480e-14a0-482e-b417-629d8829a1c0";
    String SENSOR_CHARACTERISTIC_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a0";

    public void initializeBluetoothConnection(){
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try{
                    DeviceManager.createInstance(false);
                    deviceManager = DeviceManager.getInstance();
                    BTadapter = deviceManager.getAdapters().getFirst();
                    deviceManager.setDefaultAdapter(BTadapter);
                    this.updateMessage("searching for device");
                    sensor = findDevice(ARDUINO_BLE_ADDRESS);
                    if (sensor != null) {
                        setupGattNotification(sensor);
                    }else{
                        this.updateMessage("device not found");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
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
                    Thread.sleep(500);//ensure proper connection beofre refreshing GATT
                }catch(InterruptedException ex){
                    System.out.println("Error: Thread sleep interrupted: " +ex);
                    updateMessage("Bluetooth error: " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
                BluetoothGattService Service = sensor.getGattServiceByUuid(ARDUINO_SERVICE_UUID);
                Service.refreshGattCharacteristics();
                BluetoothGattCharacteristic Gatt = Service.getGattCharacteristicByUuid(SENSOR_CHARACTERISTIC_UUID);

                try {
                    Gatt.startNotify();
                } catch (Exception ex) {
                    System.out.println("Error: Cannot start notification: " +ex);
                    updateMessage("Bluetooth error: " + ex.getMessage());
                    throw new RuntimeException(ex);
                }
                deviceManager.registerPropertyHandler(new GATTNotificationHandler(Gatt.getDbusPath()));
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public void closeConnection() {
        if (sensor != null) {
            sensor.disconnect();
        }
    }

}
