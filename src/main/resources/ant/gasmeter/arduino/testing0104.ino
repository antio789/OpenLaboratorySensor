#include <ArduinoBLE.h>

#define SENSOR_PIN_8 8
#define SENSOR_PIN_9 9


BLEService sensorService("a117480e-14a0-482e-b417-629d8829a1c0");
BLEBooleanCharacteristic cycleNotify("1d1c079e-e607-4faa-9005-7bc16934f4a0", BLERead | BLENotify);
BLEUnsignedIntCharacteristic sensorTotal("1d1c079e-e607-4faa-9005-7bc16934f4a1",BLERead);
BLEUnsignedIntCharacteristic warningNotify("1d1c079e-e607-4faa-9005-7bc16934f4a3", BLERead | BLENotify);


void setup() {
  Serial.begin(115200);
  while (!Serial);

  pinMode(SENSOR_PIN_8,INPUT);  

  if (!BLE.begin()) {
    Serial.println("starting Bluetooth® Low Energy module failed!");
    while (1);
  }
  BLE.setLocalName("ArduinoSensor");
  BLE.setAdvertisedService(sensorService);
  sensorService.addCharacteristic(cycleNotify);
  sensorService.addCharacteristic(sensorTotal);
  sensorService.addCharacteristic(warningNotify);
  BLE.addService(sensorService);
  cycleNotify.writeValue(0);
  BLE.advertise();
}

enum CycleState { IDLE, D1_ACTIVE, BOTH_ACTIVE, D2_RELEASED };
CycleState currentState = IDLE;
unsigned int count = 0;
const unsigned long STABILITY_DELAY = 80; 

bool s1_last = LOW, s2_last = LOW;
unsigned long s1_time = 0, s2_time = 0;

bool getStableRead(int pin, bool &lastState, unsigned long &lastTime) {
    bool read = digitalRead(pin);
    if (read != lastState) {
        if ((millis() - lastTime) > STABILITY_DELAY) {
            Serial.print(read);
            Serial.println(" new position");
            Serial.println(currentState);
            lastState = read;
            return read;
        }
    }
    else{
        lastTime = millis();
    }
    return lastState; // Keep previous logical state until stability reached
}

void loop() {
    BLEDevice central = BLE.central();
    
    // Logic runs regardless of BLE connection to maintain count integrity
    bool s1 = getStableRead(SENSOR_PIN_8, s1_last, s1_time);
    bool s2 = getStableRead(SENSOR_PIN_9, s2_last, s2_time);
   //0 not blocked, 1 liquid in front 
    
    switch (currentState) {
        case IDLE:
            if (s2) {// if s2 = 1  means liquid in front
                sendWarning(1); // D2 triggered before D1
            } else if (s1) { // if s1 = 1 means liquid in front 
                currentState = D1_ACTIVE;
            }
            break;

        case D1_ACTIVE:
            if (s1 && s2) {
                currentState = BOTH_ACTIVE;
            } else if (!s1) {
                sendWarning(2); // Receded before hitting D2
                currentState = IDLE;
            }
            break;

        case BOTH_ACTIVE:
            if (s1 && !s2) {
                currentState = D2_RELEASED;
            } else if (!s1 && !s2) {
                // Liquid dropped too fast or power cut
                sendWarning(4);
                count++;
                handleCycleComplete();
                currentState = IDLE;
            }
            break;

        case D2_RELEASED:
            if (!s1 && !s2) {
                count++;
                handleCycleComplete();
                currentState = IDLE;
            }else if (s2) {
                currentState = BOTH_ACTIVE; // Hovering behavior
                sendWarning(3);
            }// Liquid went back up
            break;
    }
}

unsigned int previousWarning = 0;
void sendWarning(int code) {
    if(code != previousWarning){
    Serial.print("WARNING: Error Code ");
    Serial.println(code);
    warningNotify.writeValue(code);
    previousWarning = code;
    }
}

void handleCycleComplete() {
    Serial.print("Cycle Validated. Total: ");
    Serial.println(count);

    sensorTotal.writeValue(count);
    cycleNotify.writeValue(true);
    delay(10);
    cycleNotify.writeValue(false);
}
