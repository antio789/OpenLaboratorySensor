package ant.gasmeter;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import javafx.concurrent.Task;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.List;
/**TODO rewrite the file into two different tasks: start notifying and closing bluetooth connection
 * **/
public class BLReceiver extends Task<Void> {
    BluetoothAdapter BTadapter;
    DeviceManager deviceManager;
    BluetoothDevice sensor;
    private final String bleAddress;

    public BLReceiver(String bleAddress) {
        this.bleAddress = bleAddress;
    }

    public BLReceiver() {
        this.bleAddress = "f4:12:fa:6f:85:c5";
    }

    String ARDUINO_SERVICE_UUID = "a117480e-14a0-482e-b417-629d8829a1c0";
    String SENSOR_CHARACTERISTIC_UUID = "1d1c079e-e607-4faa-9005-7bc16934f4a0";
    String DISCONNECTION = "1d1c079e-e607-4faa-9005-7bc16934f4a1";

    @Override
    protected Void call() {
        try {
            DeviceManager.createInstance(false);
            deviceManager = DeviceManager.getInstance();
            BTadapter = deviceManager.getAdapters().getFirst();
            deviceManager.setDefaultAdapter(BTadapter);
            updateMessage("searching for device");
            sensor = findAndConnectDeviceByMac(bleAddress);
            if (sensor != null) {
                while(!isCancelled()){
                    listen(sensor);
                }
                System.out.println("Listening stopped");
                sensor.disconnect();
            }
        } catch (DBusException ex) {
            System.out.println("Error: Cannot create Bluetooth device manager instance: " +ex);
            updateMessage("Bluetooth error: " + ex.getMessage());
        }
        return null;
    }

    private BluetoothDevice findAndConnectDeviceByMac(String mac){
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

    private void listen(BluetoothDevice sensor) throws DBusException {
        try{
            Thread.sleep(1000);
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
        Gatt.isNotifying();
        Gatt.getValue();
        while(!isCancelled()){
            try {
                Thread.sleep(100);
            }catch(InterruptedException ex){
                System.out.println("disconnection: " +ex);
                updateMessage("Bluetooth disconnection: " + ex.getMessage());
            }
        }
    }

}
