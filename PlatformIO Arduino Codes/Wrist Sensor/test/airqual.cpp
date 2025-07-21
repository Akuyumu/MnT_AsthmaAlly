#include "DFRobot_MICS.h"

#define ADC_PIN   A0      // Analog out from sensor to A0
#define POWER_PIN 1      // EN pin to D2

DFRobot_MICS_ADC mics(ADC_PIN, POWER_PIN);

void setup() {
  Serial.begin(115200);
  while (!Serial);
  while (!mics.begin()) {
    Serial.println("NO Devices !");
    delay(1000);
  }
  Serial.println("Device connected successfully!");
  
  // Wake up sensor if needed
  if (mics.getPowerState() == SLEEP_MODE) {
    mics.wakeUpMode();
    Serial.println("wake up sensor success!");
  }
  
  // Warm-up time (default 3 minutes, you may need longer for stable results)
  while (!mics.warmUpTime(180000)) {
    Serial.println("Please wait until the warm-up time is over!");
    delay(1000);
  }
  Serial.println("Sensor is ready for use.");
}

void loop() {
  float co = mics.getGasData(CO);
  float ch4 = mics.getGasData(CH4);
  float c2h5oh = mics.getGasData(C2H5OH);
  float h2 = mics.getGasData(H2);
  float nh3 = mics.getGasData(NH3);

  Serial.print("CO: "); Serial.print(co); Serial.print(" ppm | ");
  Serial.print("CH4: "); Serial.print(ch4); Serial.print(" ppm | ");
  Serial.print("Ethanol: "); Serial.print(c2h5oh); Serial.print(" ppm | ");
  Serial.print("H2: "); Serial.print(h2); Serial.print(" ppm | ");
  Serial.print("NH3: "); Serial.print(nh3); Serial.println(" ppm");

  delay(1000); // Wait one second between readings
}
