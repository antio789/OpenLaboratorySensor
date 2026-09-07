package ant.gasmeter;

package com.github.hypfvieh.sandbox.bluez;

import org.bluez.GattCharacteristic1;
import org.bluez.GattDescriptor1;
import org.bluez.GattManager1;
import org.bluez.GattService1;
import org.bluez.datatypes.TwoTuple;
import org.bluez.exceptions.*;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.FileDescriptor;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.Variant;

import java.util.*;

public class bleexample {
    static final String FLAG_READ = "read";
    static final String FLAG_WRITE = "write";

    public static void main(String[] args) {

        MyGattApplication myGattApplication = new MyGattApplication();
        SampleService sampleService = new SampleService();
        SampleCharacteristics sampleCharacteristics = new SampleCharacteristics(sampleService);
        SampleGattDescriptor sampleDescriptor = new SampleGattDescriptor(sampleCharacteristics);
        sampleCharacteristics.getDescriptors().add(sampleDescriptor);
        myGattApplication.addService(sampleService);

        sampleService.getCharacteristics().add(sampleCharacteristics);

        try (DBusConnection dbus = DBusConnectionBuilder.forSystemBus().build()) {

            dbus.exportObject(myGattApplication);

            GattManager1 gattMgr = dbus.getRemoteObject("org.bluez", "/org/bluez/hci0", GattManager1.class);

            Map<String, Variant<?>> opts = new HashMap<>();

            gattMgr.RegisterApplication(new DBusPath(myGattApplication.getObjectPath()), opts);
            Thread.sleep(50000000L); // do something useful or a proper wait loop
        } catch (Exception _ex) {
            // TODO Auto-generated catch block
            _ex.printStackTrace();
        }
    }

    public static class MyGattApplication implements GattServiceApp {

        private final List<SampleService> services = new ArrayList<>();

        @Override
        public String getObjectPath() {
            return "/";
        }

        public void addService(SampleService _service) {
            services.add(_service);
        }

        @Override
        public Map<DBusPath, Map<String, Map<String, Variant<?>>>> GetManagedObjects() {

            Map<DBusPath, Map<String, Map<String, Variant<?>>>> result = new LinkedHashMap<>();

            for (SampleService svc : services) {
                result.put(new DBusPath(svc.getObjectPath()), svc.getProps());
                for (SampleCharacteristics chr : svc.getCharacteristics()) {
                    result.put(new DBusPath(chr.getObjectPath()), chr.getProps());
                    for (SampleGattDescriptor descriptor : chr.getDescriptors()) {
                        result.put(new DBusPath(descriptor.getObjectPath()), descriptor.getProps());
                    }
                }
            }

            return result;
        }

    }

    public static class SampleService implements GattService1, PropertiesHelper {
        static final String BASE_PATH = "/mygatt/application/";
        // This should be a static value, not a random one
        private final String uuid = UUID.randomUUID().toString();
        private final Boolean primary = true;
        private final List<SampleCharacteristics> characteristics = new ArrayList<>();

        public List<SampleCharacteristics> getCharacteristics() {
            return characteristics;
        }

        @Override
        public String getObjectPath() {
            return BASE_PATH + "service0";
        }

        public String getUuid() {
            return uuid;
        }

        public boolean isPrimary() {
            return primary;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Variant<?> Get(String _interfaceName, String _propertyName) {
            if (!getBluezInterface().getName().equals(_interfaceName)) {
                throw new IllegalArgumentException();
            }

            if ("UUID".equals(_propertyName)) {
                return new Variant<>(uuid);
            } else if ("Primary".equals(_propertyName)) {
                return new Variant<>(primary);
            } else if ("Characteristics".equals(_propertyName)) {
                return getCharacteristicsAsVariantArray();
            }
            return null;
        }

        @Override
        public <A> void Set(String _interfaceName, String _propertyName, A _value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Variant<?>> GetAll(String _interfaceName) {
            return Map.of("UUID", new Variant<>(uuid),
                    "Primary", new Variant<>(primary),
                    "Characteristics", getCharacteristicsAsVariantArray()
            );
        }

        @Override
        public Class<? extends DBusInterface> getBluezInterface() {
            return GattService1.class;
        }

        private Variant<List<DBusPath>> getCharacteristicsAsVariantArray() {
            List<DBusPath> pathes = new ArrayList<>();
            getCharacteristics().stream().map(d -> new DBusPath(d.getObjectPath())).forEach(pathes::add);

            return new Variant<>(pathes, "ao");
        }

    }

    public static class SampleCharacteristics implements GattCharacteristic1, PropertiesHelper {

        private final String uuid = UUID.randomUUID().toString();

        private final SampleService service;
        private final List<String> flags = new ArrayList<>();
        private final List<SampleGattDescriptor> descriptors = new ArrayList<>();

        public SampleCharacteristics(SampleService _service) {
            service = _service;
            flags.add(FLAG_READ);
        }

        public String getUuid() {
            return uuid;
        }

        public SampleService getService() {
            return service;
        }

        public List<SampleGattDescriptor> getDescriptors() {
            return descriptors;
        }

        @Override
        public Class<? extends DBusInterface> getBluezInterface() {
            return GattCharacteristic1.class;
        }

        @Override
        public String getObjectPath() {
            return service.getObjectPath() + "/char0";
        }

        @Override
        public byte[] ReadValue(Map<String, Variant<?>> _options)
                throws BluezFailedException, BluezInProgressException, BluezNotPermittedException, BluezNotAuthorizedException, BluezInvalidOffsetException, BluezNotSupportedException {

            throw new UnsupportedOperationException("Implement if needed");
        }

        @Override
        public void WriteValue(byte[] _value, Map<String, Variant<?>> _options)
                throws BluezFailedException, BluezInProgressException, BluezNotPermittedException, BluezInvalidValueLengthException, BluezNotAuthorizedException, BluezNotSupportedException {
            throw new UnsupportedOperationException("Implement if needed");

        }

        @Override
        public TwoTuple<FileDescriptor, UInt16> AcquireWrite(Map<String, Variant<?>> _options) throws BluezFailedException, BluezNotSupportedException {
            throw new UnsupportedOperationException("Implement if needed");
        }

        @Override
        public TwoTuple<FileDescriptor, UInt16> AcquireNotify(Map<String, Variant<?>> _options) throws BluezFailedException, BluezNotSupportedException {
            throw new UnsupportedOperationException("Implement if needed");
        }

        @Override
        public void StartNotify() throws BluezFailedException, BluezNotPermittedException, BluezInProgressException, BluezNotConnectedException, BluezNotSupportedException {
            throw new UnsupportedOperationException("Implement if needed");

        }

        @Override
        public void StopNotify() throws BluezFailedException {
            throw new UnsupportedOperationException("Implement if needed");

        }

        @Override
        public void Confirm() throws BluezFailedException {
            throw new UnsupportedOperationException("Implement if needed");
        }

        @SuppressWarnings("unchecked")
        @Override
        public Variant<?> Get(String _interfaceName, String _propertyName) {
            if (!getBluezInterface().getName().equals(_interfaceName)) {
                throw new IllegalArgumentException();
            }

            if ("UUID".equals(_propertyName)) {
                return  new Variant<>(uuid);
            } else if ("Service".equals(_propertyName)) {
                return new Variant<>(new DBusPath(service.getObjectPath()));
            } else if ("Descriptors".equals(_propertyName)) {
                return getDescriptorsAsVariantArray();
            } else if ("Flags".equals(_propertyName)) {
                return  new Variant<>(flags, "as");
            }
            return null;
        }

        @Override
        public <A> void Set(String _interfaceName, String _propertyName, A _value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Variant<?>> GetAll(String _interfaceName) {
            return Map.of("UUID", new Variant<>(uuid),
                    "Service", new Variant<>(new DBusPath(service.getObjectPath())),
                    "Descriptors", getDescriptorsAsVariantArray(),
                    "Flags", new Variant<>(flags, "as"));
        }

        private Variant<List<DBusPath>> getDescriptorsAsVariantArray() {
            List<DBusPath> pathes = new ArrayList<>();
            getDescriptors().stream().map(d -> new DBusPath(d.getObjectPath())).forEach(pathes::add);

            return new Variant<>(pathes, "ao");
        }

    }

    public static class SampleGattDescriptor implements GattDescriptor1, PropertiesHelper {

        private final String uuid = UUID.randomUUID().toString();
        private final SampleCharacteristics characteristic;
        private final List<String> flags = new ArrayList<>();

        public SampleGattDescriptor(SampleCharacteristics _characteristics) {
            characteristic = _characteristics;
            flags.add(FLAG_READ);
        }

        public String getUuid() {
            return uuid;
        }

        public SampleCharacteristics getCharacteristic() {
            return characteristic;
        }

        @Override
        public Class<? extends DBusInterface> getBluezInterface() {
            return GattDescriptor1.class;
        }

        @Override
        public String getObjectPath() {
            return characteristic.getObjectPath() + "/desc0";
        }

        @Override
        public byte[] ReadValue(Map<String, Variant<?>> _flags)
                throws BluezFailedException, BluezInProgressException, BluezNotPermittedException, BluezNotAuthorizedException, BluezNotSupportedException {
            throw new UnsupportedOperationException("Implement if needed");
        }

        @Override
        public void WriteValue(byte[] _value, Map<String, Variant<?>> _flags)
                throws BluezFailedException, BluezInProgressException, BluezNotPermittedException, BluezInvalidValueLengthException, BluezNotAuthorizedException, BluezNotSupportedException {
            throw new UnsupportedOperationException("Implement if needed");
        }

        @SuppressWarnings("unchecked")
        @Override
        public Variant<?> Get(String _interfaceName, String _propertyName) {
            if ("UUID".equals(_propertyName)) {
                return new Variant<>(uuid);
            } else if ("Characteristic  ".equals(_propertyName)) {
                return new Variant<>(characteristic);
            } else if ("Flags".equals(_propertyName)) {
                return new Variant<>(flags, "as");
            }
            return null;
        }

        @Override
        public <A> void Set(String _interfaceName, String _propertyName, A _value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Variant<?>> GetAll(String _interfaceName) {
            return Map.of("UUID", new Variant<>(uuid),
                    "Characteristic", new Variant<>(new DBusPath(characteristic.getObjectPath())),
                    "Flags", new Variant<>(flags, "as"));
        }


    }

    public interface GattServiceApp extends DBusInterface {

        Map<DBusPath, Map<String, Map<String, Variant<?>>>> GetManagedObjects();
    }

    public interface PropertiesHelper extends Properties {

        Class<? extends DBusInterface> getBluezInterface();

        default Map<String, Map<String, Variant<?>>> getProps() {
            Map<String, Map<String, Variant<?>>> result = new LinkedHashMap<>();
            result.put(getBluezInterface().getName(), GetAll(null));
            return result;
        }


    }
}

