package ant.gasmeter;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import org.bluez.Adapter1;

import java.io.Closeable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import org.freedesktop.dbus.exceptions.DBusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;


public class BLEconnection implements Closeable {
    private DeviceManager deviceManager;
    private BluetoothAdapter adapter;
    private BluetoothDevice device;

    /**
     * Initialize the DeviceManager and pick an adapter by name (e.g. "hci0").
     */
    public void init(String adapterName) throws DBusException {
        deviceManager = DeviceManager.createInstance(false);
        this.deviceManager.scanForBluetoothDevices(1000);

        adapter = deviceManager.getAdapters()
                .stream()
                .filter(a -> adapterName.equalsIgnoreCase(a.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Adapter not found: " + adapterName));

        // Ensure the adapter is powered and LE discovery enabled
        if (!adapter.isPowered()) {
            adapter.setPowered(true);
        }
        if (adapter.isDiscovering()) {
            adapter.stopDiscovery();
        }
    }

    /**
     * Discover and connect to a device by Bluetooth MAC address.
     * Example mac: "AA:BB:CC:DD:EE:FF"
     */
    public void connectByAddress(String mac, long discoverySeconds) throws InterruptedException {
        ensureAdapter();
        adapter.startDiscovery();
        try {
            // Allow some time for discovery to populate the cache
            TimeUnit.SECONDS.sleep(discoverySeconds);

            device = deviceManager.getDevices().stream()
                    .filter(d -> mac.equalsIgnoreCase(d.getAddress()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Device not found: " + mac));

            connectDevice();
        } finally {
            adapter.stopDiscovery();
        }
    }


    /**
     * Read a characteristic by service and characteristic UUIDs.
     */
    public byte[] readCharacteristic(UUID serviceUuid, UUID characteristicUuid) {
        ensureConnected();
        BluetoothGattCharacteristic ch = findCharacteristic(serviceUuid, characteristicUuid);
        byte[] value = ch.readValue();
        if (value == null) {
            throw new IllegalStateException("Read returned null for characteristic: " + characteristicUuid);
        }
        return value;
    }

    /**
     * Write a characteristic by service and characteristic UUIDs.
     * withResponse=false may be faster; set true if your device requires a response.
     */
    public void writeCharacteristic(UUID serviceUuid, UUID characteristicUuid, byte[] data, boolean withResponse) {
        ensureConnected();
        BluetoothGattCharacteristic ch = findCharacteristic(serviceUuid, characteristicUuid);
        // Library versions expose either writeValue(byte[]) or writeValue(byte[], boolean)
        try {
            ch.writeValue(data, !withResponse); // try signature with 'withoutResponse'
        } catch (Throwable ignored) {
            // fallback to basic signature if available
            ch.writeValue(data);
        }
    }

    /**
     * Convenience methods to read/write strings (UTF-8).
     */
    public String readCharacteristicAsString(UUID serviceUuid, UUID characteristicUuid) {
        return new String(readCharacteristic(serviceUuid, characteristicUuid), StandardCharsets.UTF_8);
    }

    public void writeCharacteristic(String text, UUID serviceUuid, UUID characteristicUuid, boolean withResponse) {
        writeCharacteristic(serviceUuid, characteristicUuid, text.getBytes(StandardCharsets.UTF_8), withResponse);
    }

    private void connectDevice() {
        if (!device.isConnected()) {
            device.connect();
        }
        // Resolve services (BlueZ may lazily load; calling getGattServices ensures they are available)
        device.getGattServices();
    }

    private BluetoothGattCharacteristic findCharacteristic(UUID serviceUuid, UUID characteristicUuid) {
        List<BluetoothGattService> services = device.getGattServices();
        if (services == null || services.isEmpty()) {
            throw new IllegalStateException("No GATT services discovered");
        }
        BluetoothGattService svc = services.stream()
                .filter(s -> uuidEquals(s.getUuid(), serviceUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Service not found: " + serviceUuid));

        return svc.getCharacteristics().stream()
                .filter(c -> uuidEquals(c.getUuid(), characteristicUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Characteristic not found: " + characteristicUuid));
    }

    private static boolean uuidEquals(String bluezUuid, UUID uuid) {
        // BlueZ reports lowercase 128-bit UUIDs
        return bluezUuid != null && bluezUuid.equalsIgnoreCase(uuid.toString());
    }

    private void ensureAdapter() {
        if (deviceManager == null || adapter == null) {
            throw new IllegalStateException("Call init(adapterName) first");
        }
    }

    private void ensureConnected() {
        ensureAdapter();
        if (device == null || !device.isConnected()) {
            throw new IllegalStateException("Device is not connected");
        }
    }

    @Override
    public void close() throws IOException {
        if (device != null) {
            try {
                if (device.isConnected()) {
                    device.disconnect();
                }
            } catch (Exception ignored) {}
            device = null;
        }
        if (adapter != null) {
            try {
                if (adapter.isDiscovering()) {
                    adapter.stopDiscovery();
                }
            } catch (Exception ignored) {}
        }
        if (deviceManager != null) {
            deviceManager.close();
            deviceManager = null;
        }
    }
}


}
