#include <ArduinoBLE.h>

// Maximum sensors per experiment. Limited by the board's available digital pins
// (e.g. Uno/Nano: D2-D13 + A0-A5 = 18 pins = 9 sensor pairs) and the sensorCountCombo in view.fxml.
#define MAX_SENSORS 5

BLEService sensorService("a117480e-14a0-482e-b417-629d8829a1c0");
BLEUnsignedIntCharacteristic cycleNotify("1d1c079e-e607-4faa-9005-7bc16934f4a0", BLERead | BLENotify);
BLEUnsignedIntCharacteristic sensorTotal("1d1c079e-e607-4faa-9005-7bc16934f4a1",BLERead);
// Warning codes reported through warningNotify:
// 0  - no warning (initial state)
// 1  - SEQUENCE VIOLATION: upper sensor triggered before the lower sensor (liquid passed S2 first)
// 2  - SEQUENCE VIOLATION: lower sensor receded before the upper sensor was reached
// 3  - hovering detected on the upper sensor (unstable liquid level)
// 4  - liquid dropped fast or power cut: both sensors disconnected simultaneously (cycle counted)
// 5  - invalid sensor pin config received from the application (empty or malformed)
// 6  - Bluetooth Low Energy module failed to start at boot (sent once the module recovers)
BLEUnsignedIntCharacteristic warningNotify("1d1c079e-e607-4faa-9005-7bc16934f4a3", BLERead | BLENotify);
// config string format: "lower1,upper1;lower2,upper2" sensor numbers are 1-based
// value size is the max config string length in bytes (BLE allows up to 512); large enough for MAX_SENSORS entries
BLECharacteristic sensorConfig("1d1c079e-e607-4faa-9005-7bc16934f4a4", BLERead | BLEWrite, 120);

enum CycleState { IDLE, D1_ACTIVE, BOTH_ACTIVE, D2_RELEASED };

struct SensorState {
    CycleState state = IDLE;
    bool s1_last = LOW, s2_last = LOW;
    unsigned long s1_time = 0, s2_time = 0;
};

int sensorCount = 0;
int lowerPins[MAX_SENSORS] = {0};
int upperPins[MAX_SENSORS] = {0};
SensorState sensors[MAX_SENSORS];
unsigned int count = 0;
const unsigned long STABILITY_DELAY = 80;
unsigned int previousWarning = 0;

void setup() {
  Serial.begin(115200);
  while (!Serial);

  bool bleFailedAtStart = false;
  if (!BLE.begin()) {
    Serial.println("starting Bluetooth® Low Energy module failed!");
    bleFailedAtStart = true;
    while (!BLE.begin()) {
      delay(1000); // retry until the module starts; a BLE warning is impossible while it is down
    }
  }
  BLE.setLocalName("ArduinoSensor");
  BLE.setAdvertisedService(sensorService);
  sensorService.addCharacteristic(cycleNotify);
  sensorService.addCharacteristic(sensorTotal);
  sensorService.addCharacteristic(warningNotify);
  sensorService.addCharacteristic(sensorConfig);
  BLE.addService(sensorService);
  cycleNotify.writeValue(0);
  sensorConfig.writeValue("");
  BLE.advertise();
  if (bleFailedAtStart) {
    sendWarning(6);
  }
}

// error code 5: malformed pin config (missing ',' or ';' separator)
void applyConfig() {
    String cfg = sensorConfig.value();
    if (cfg.length() == 0) {
        sendWarning(5);
        return;
    }
    Serial.print("Applying config: ");
    Serial.println(cfg);
    int newLower[MAX_SENSORS];
    int newUpper[MAX_SENSORS];
    int idx = 0;
    int pos = 0;
    while (idx < MAX_SENSORS) {
        int semi = cfg.indexOf(';', pos);
        String pair = (semi == -1) ? cfg.substring(pos) : cfg.substring(pos, semi);
        int comma = pair.indexOf(',');
        if (comma == -1) {
            Serial.print("Malformed config at pair: ");
            Serial.println(pair);
            sendWarning(5);
            return;
        }
        int lower = pair.substring(0, comma).toInt();
        int upper = pair.substring(comma + 1).toInt();
        if (lower <= 0 || upper <= 0) {
            Serial.print("Invalid pin numbers in pair: ");
            Serial.println(pair);
            sendWarning(5);
            return;
        }
        newLower[idx] = lower;
        newUpper[idx] = upper;
        idx++;
        if (semi == -1) break;
        pos = semi + 1;
    }
    // only apply once the whole config parsed successfully
    for (int i = 0; i < idx; i++) {
        lowerPins[i] = newLower[i];
        upperPins[i] = newUpper[i];
        pinMode(lowerPins[i], INPUT);
        pinMode(upperPins[i], INPUT);
        sensors[i] = SensorState();
    }
    sensorCount = idx;
    previousWarning = 0; // re-arm warnings for the new configuration
    Serial.print("Config applied for ");
    Serial.print(sensorCount);
    Serial.println(" sensor(s)");
}

bool getStableRead(int pin, bool &lastState, unsigned long &lastTime) {
    bool read = digitalRead(pin);
    if (read != lastState) {
        if ((millis() - lastTime) > STABILITY_DELAY) {
            Serial.print(read);
            Serial.println(" new position");
            lastState = read;
            return read;
        }
    }
    else{
        lastTime = millis();
    }
    return lastState;
}

void sendWarning(int code) {
    if(code != previousWarning){
        Serial.print("WARNING: Error Code ");
        Serial.println(code);
        warningNotify.writeValue(code);
        previousWarning = code;
    }
}

void handleCycleComplete(int sensor) {
    Serial.print("Cycle Validated. Sensor: ");
    Serial.print(sensor);
    Serial.print(" Total: ");
    Serial.println(count);

    sensorTotal.writeValue(count);
    cycleNotify.writeValue(sensor);
}

void runSensor(int i) {
    SensorState &s = sensors[i];
    bool s1 = getStableRead(lowerPins[i], s.s1_last, s.s1_time);
    bool s2 = getStableRead(upperPins[i], s.s2_last, s.s2_time);
    //0 not blocked, 1 liquid in front

    switch (s.state) {
        case IDLE:
            if (s2) {
                sendWarning(1); // D2 triggered before D1
            } else if (s1) {
                s.state = D1_ACTIVE;
            }
            break;

        case D1_ACTIVE:
            if (s1 && s2) {
                s.state = BOTH_ACTIVE;
            } else if (!s1) {
                sendWarning(2); // Receded before hitting D2
                s.state = IDLE;
            }
            break;

        case BOTH_ACTIVE:
            if (s1 && !s2) {
                s.state = D2_RELEASED;
            } else if (!s1 && !s2) {
                // Liquid dropped too fast or power cut
                sendWarning(4);
                count++;
                handleCycleComplete(i + 1);
                s.state = IDLE;
            }
            break;

        case D2_RELEASED:
            if (!s1 && !s2) {
                count++;
                handleCycleComplete(i + 1);
                s.state = IDLE;
            } else if (s2) {
                s.state = BOTH_ACTIVE; // Hovering behavior
                sendWarning(3);
            }
            break;
    }
}

void loop() {
    BLEDevice central = BLE.central();
    if (central.connected() && sensorConfig.written()) {
        applyConfig();
    }

    for (int i = 0; i < sensorCount; i++) {
        runSensor(i);
    }
}
