package ant.gasmeter;

import java.util.List;

public class BLEConnectionConfig {
    public record BLEConnectionConfigData(String mac, String uuid, String deviceName, List<SensorPins> sensorPins) {}
    public record SensorPins(String lower, String upper) {}
}
