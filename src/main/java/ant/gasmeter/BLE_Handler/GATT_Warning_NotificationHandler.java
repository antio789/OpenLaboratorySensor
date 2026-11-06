package ant.gasmeter.BLE_Handler;

import ant.gasmeter.utils.Logger;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;

//receives the warning codes sent by the arduino through the warning characteristic and logs them
public class GATT_Warning_NotificationHandler extends AbstractPropertiesChangedHandler {
    private final String CharacterPath;
    private final Logger logger;

    public GATT_Warning_NotificationHandler(String path, Logger _logger) {
        this.CharacterPath = path;
        logger = _logger;
    }

    @Override
    public void handle(Properties.PropertiesChanged _signal) {
        if (!CharacterPath.equals(_signal.getPath())) {
            return;
        }
        Map<String, Variant<?>> propMap = _signal.getPropertiesChanged();
        if (!propMap.containsKey("Value")) {
            return;
        }
        Object value = propMap.get("Value").getValue();
        if (value instanceof List<?> byteArr && !byteArr.isEmpty()) {
            Object first = byteArr.getFirst();
            int code = 0;
            if (first instanceof Byte b) {
                code = b & 0xFF;
            } else if (first instanceof Number n) {
                code = n.intValue();
            }
            if (code > 0 && logger != null) {
                logger.log("ARDUINO WARNING " + code + ": " + BLEReceiver.warningMessages.getOrDefault(code, "unknown warning code"));
            }
        }
    }
}
