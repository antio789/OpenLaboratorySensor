package ant.gasmeter;

import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;

public class GATTNotificationHandler extends AbstractPropertiesChangedHandler {

    private final String CharacterPath;

    public GATTNotificationHandler(String path) {
        this.CharacterPath = path;
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
            } else if (first instanceof Number n) {
                System.out.println("Received number: " + n);
            }

        }else{
            System.err.println("Received signal with wrong value type: " + value.getClass().getName());
        }

    }
}
