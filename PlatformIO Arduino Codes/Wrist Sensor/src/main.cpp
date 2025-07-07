#include <Arduino.h>
#include <Wire.h> // I2C library
#include "Adafruit_TinyUSB.h"
#include <Adafruit_NeoPixel.h> // Neopixel LED library
#include "MAX30105.h" // MAX30105 or MAx30102 library
#include "spo2_algorithm.h" //SpO2 Sensor library

#define VBATPIN A6 // Battery voltage analog pin
#define NEOPIXELPIN 8 // Neopixel control pin
#define NUMPIXELS 1 // Number of neopixels
#define MAX_BRIGHTNESS 255 // for MAX30102
#define INBUILTLED_PIN 13 // Inbuilt red led used to indicate low SpO2 levels

// Put variable declaration here
float VBat;

//Initialise MAX30102 SpO2 Sensor
MAX30105 particleSensor;

#if defined(__AVR_ATmega328P__) || defined(__AVR_ATmega168__)
//Arduino Uno doesn't have enough SRAM to store 100 samples of IR led data and red led data in 32-bit format
//To solve this problem, 16-bit MSB of the sampled data will be truncated. Samples become 16-bit data.
uint16_t irBuffer[100]; //infrared LED sensor data
uint16_t redBuffer[100];  //red LED sensor data
#else
uint32_t irBuffer[100]; //infrared LED sensor data
uint32_t redBuffer[100];  //red LED sensor data
#endif

int32_t bufferLength; //data length
int32_t spo2; //SPO2 value
int8_t validSPO2; //indicator to show if the SPO2 calculation is valid
int32_t heartRate; //heart rate value
int8_t validHeartRate; //indicator to show if the heart rate calculation is valid

// Put function declaration here
float getBattVoltage(int x);
void VBatIndicator();
void FillSensorBuffer();
void FillSensorBuffer(int32_t samplesize);

// Initialize NeoPixel
Adafruit_NeoPixel pixel = Adafruit_NeoPixel(NUMPIXELS, NEOPIXELPIN, NEO_GRB + NEO_KHZ800);

void setup() {
  // put your setup code here, to run once:

  pinMode(INBUILTLED_PIN, OUTPUT);  // Set LED pin as output
  digitalWrite(INBUILTLED_PIN, LOW);  // Ensure LED starts off

  // Initialize NeoPixel
  pixel.begin();
  pixel.setBrightness(2); // Set brightness (0-255)

  // Quick test of NeoPixel
  pixel.setPixelColor(0, pixel.Color(255, 0, 0));  // Red
  pixel.show();
  delay(500);
  pixel.setPixelColor(0, pixel.Color(0, 255, 0));  // Green
  pixel.show();
  delay(500);
  pixel.setPixelColor(0, pixel.Color(0, 0, 255));  // Blue
  pixel.show();
  delay(500);
  pixel.setPixelColor(0, pixel.Color(0, 0, 0));  // Blue
  pixel.show();
  delay(500);

  // Start Serial
  Serial.begin(115200);

  // Initialize MAX30102
  particleSensor.begin(Wire, I2C_SPEED_FAST);

  byte ledBrightness = 0x7F; //Options: 0=Off to 255=50mA
  byte sampleAverage = 4; //Options: 1, 2, 4, 8, 16, 32
  byte ledMode = 2; //Options: 1 = Red only, 2 = Red + IR, 3 = Red + IR + Green
  int sampleRate = 400; //Options: 50, 100, 200, 400, 800, 1000, 1600, 3200
  int pulseWidth = 411; //Options: 69, 118, 215, 411
  int adcRange = 16384; //Options: 2048, 4096, 8192, 16384

  particleSensor.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange); //Configure sensor with these settings
}

void loop() {
    // put your main code here, to run repeatedly
    // Sequence to indicate battery status
    VBat =  getBattVoltage(analogRead(VBATPIN)); 
    VBatIndicator();

    // Check for finger presence first
    if (particleSensor.getIR() < 5000) {
        Serial.println("No finger detected");
        delay(1000);
        return;
    }
  
    // Fill data sets in buffer with 100 new sets of measurements
    bufferLength = 100;
    FillSensorBuffer(bufferLength);

    //calculate heart rate and SpO2 after 100 samples loaded into buffer
    maxim_heart_rate_and_oxygen_saturation(irBuffer, bufferLength, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);

    if (validHeartRate == 1 && particleSensor.getIR() > 5000) {
      Serial.print(heartRate);
      Serial.print(" BPM; ");
    } else{
      Serial.print(validHeartRate);
      Serial.print(" BPM; ");
    }

    if (validSPO2 == 1 && particleSensor.getIR() > 5000) {
      Serial.print(spo2);
      Serial.println("%; ");
    } else{
      Serial.print(validSPO2);
      Serial.println("%; ");      
    }

    if (validSPO2 == 1 && spo2 < 92 && particleSensor.getIR() > 5000) {
        digitalWrite(INBUILTLED_PIN, HIGH);  // Turn on warning LED
    } else {
        digitalWrite(INBUILTLED_PIN, LOW);   // Turn off LED if SpO2 is normal
    }
    delay(10);
}

// put function definitions here
float getBattVoltage(int x) {
  float voltage = (x * 2 * 3.6) / 1024.0; 
  return constrain(voltage, 0.0, 4.2);  // LiPo max voltage is 4.2V
}

void VBatIndicator() {
    if (VBat > 4.1) {
    pixel.setPixelColor(0, pixel.Color(0, 255, 0)); // Green indicate full batt
  }
  else if (VBat < 3.4) {
    pixel.setPixelColor(0, pixel.Color(255, 0, 0)); // Red indicate low batt
  }
  else {
    pixel.setPixelColor(0, pixel.Color(0, 0, 255)); // Blue indicate otherwise
  }
  pixel.show();  // Call show() only once after setting the color
}

void FillSensorBuffer(int32_t samplesize) {   
    for (byte i = 0 ; i < samplesize ; i++) {
        // Wait for stable reading
        unsigned long startTime = millis();
        while (!particleSensor.available()) {
            particleSensor.check();
            if (millis() - startTime > 100) break;
        }
        
        redBuffer[i] = particleSensor.getRed();
        irBuffer[i] = particleSensor.getIR();
        
        particleSensor.nextSample();
        if (i == 0) {
          Serial.print("Progress Bar: [");
        }
        if (i % 5 == 0) {
          Serial.print("=");
        }
        if (i == 99) {
          Serial.println("]");
        }
      }
}