package ant.gasmeter.BLE_Handler;

import ant.gasmeter.utils.ExperimentFileManager;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;

public class GATT_Cycle_NotificationHandler extends AbstractPropertiesChangedHandler {
    private final String CharacterPath;
    private final ExperimentFileManager DATA;

    public GATT_Cycle_NotificationHandler(String path, ExperimentFileManager _data) {
        this.CharacterPath = path;
        DATA = _data;
    }

    @Override
    public void handle(Properties.PropertiesChanged _signal) {
        if(!CharacterPath.equals(_signal.getPath())) {
            return;
        }
        Map<String, Variant<?>> propMap = _signal.getPropertiesChanged();
        if (!propMap.containsKey("Value")) {
            return;
        }
        Object value = propMap.get("Value").getValue();
        if(value instanceof List<?> byteArr && !byteArr.isEmpty()){
            Object first = byteArr.getFirst();
            if (first instanceof Byte b) {
                int sensor = b & 0xFF;
                if (sensor > 0) {
                    System.out.println("Cycle completed by sensor " + sensor);
                    DATA.writeCycleToCSV(sensor);
                }
            } else if (first instanceof Number n) {
                int sensor = n.intValue();
                if (sensor > 0) {
                    System.out.println("Cycle completed by sensor " + sensor);
                    DATA.writeCycleToCSV(sensor);
                }
            }
        }else{
            System.err.println("Received signal with wrong value type: " + value.getClass().getName());
        }

    }
}
