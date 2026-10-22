#include <ArduinoBLE.h>

#define MAX_SENSORS 5

BLEService sensorService("a117480e-14a0-482e-b417-629d8829a1c0");
BLEUnsignedIntCharacteristic cycleNotify("1d1c079e-e607-4faa-9005-7bc16934f4a0", BLERead | BLENotify);
BLEUnsignedIntCharacteristic sensorTotal("1d1c079e-e607-4faa-9005-7bc16934f4a1",BLERead);
BLEUnsignedIntCharacteristic warningNotify("1d1c079e-e607-4faa-9005-7bc16934f4a3", BLERead | BLENotify);
// config string format: "lower1,upper1;lower2,upper2" sensor numbers are 1-based
BLECharacteristic sensorConfig("1d1c079e-e607-4faa-9005-7bc16934f4a4", BLERead | BLEWrite, 60);

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

  if (!BLE.begin()) {
    Serial.println("starting Bluetooth® Low Energy module failed!");
    while (1);
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
}

void applyConfig() {
    String cfg = sensorConfig.value();
    if (cfg.length() == 0) return;
    Serial.print("Applying config: ");
    Serial.println(cfg);
    int idx = 0;
    int pos = 0;
    while (idx < MAX_SENSORS) {
        int semi = cfg.indexOf(';', pos);
        String pair = (semi == -1) ? cfg.substring(pos) : cfg.substring(pos, semi);
        int comma = pair.indexOf(',');
        if (comma == -1) break;
        lowerPins[idx] = pair.substring(0, comma).toInt();
        upperPins[idx] = pair.substring(comma + 1).toInt();
        pinMode(lowerPins[idx], INPUT);
        pinMode(upperPins[idx], INPUT);
        sensors[idx] = SensorState();
        idx++;
        if (semi == -1) break;
        pos = semi + 1;
    }
    sensorCount = idx;
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
