package ant.gasmeter.BLE_Handler;

import ant.gasmeter.DataManagement;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;

public class GATTNotificationHandler extends AbstractPropertiesChangedHandler {
    private final String CharacterPath;
    private final DataManagement DATA;

    public GATTNotificationHandler(String path, DataManagement _data) {
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
                boolean valueBool = b != 0;
                System.out.println("Gas value changed to " + valueBool);
                DATA.writeStateToCSV(valueBool ? "true" : "false");
            } else if (first instanceof Number n) {
                System.out.println("Received number: " + n);
                DATA.writeStateToCSV(n.toString());
            }
        }else{
            System.err.println("Received signal with wrong value type: " + value.getClass().getName());
        }

    }
}
