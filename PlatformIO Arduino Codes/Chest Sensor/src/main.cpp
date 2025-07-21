#include <Arduino.h>
#include <Adafruit_NeoPixel.h> // NeoPixel LED library
#include "Adafruit_TinyUSB.h" 
#include <PDM.h>
#include <Adafruit_LSM6DS33.h>
#include <bluefruit.h> //BLE nRF library
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>

#define VBATPIN A6 // Battery voltage analog pin
#define NEOPIXELPIN 8 // Neopixel control pin
#define NUMPIXELS 1 // Number of neopixels
#define WHEATBRIDGEPIN_A A3 
#define WHEATBRIDGEPIN_B A4
#define ELECTRETPIN A5

// Put variable declaration here
float VBat; // Voltage of battery
int32_t mic; 
int32_t intmic; 

extern PDMClass PDM;
short PDMsampleBuffer[256];  // buffer to read PDMsamples into, each sample is 16-bits
volatile int PDMsamplesRead; // number of PDMsamples read

Adafruit_LSM6DS33 lsm6ds33;
const float ALPHA = 0.95;  // Low-pass filter coefficient (Higher values (closer to 1) mean slower gravity filtering but better noise reduction)
float gravityX = 0, gravityY = 0, gravityZ = 0;        // Filtered gravity components
float lastAccelX = 0, lastAccelY = 0, lastAccelZ = 0;  // Previous acceleration readings
float deltaX = 0, deltaY = 0, deltaZ = 0;              // Change in acceleration

// Put your function declarations here
float getBattVoltage(int x);
void VBatIndicator();
float getFilteredAcceleration();
float getPotDiffWheat();
int32_t getElectretWave();
void sendBLEData(const String& dataString);

int32_t getPDMwave(int32_t samples);
void onPDMdata();

// Initialize NeoPixel
Adafruit_NeoPixel pixel = Adafruit_NeoPixel(NUMPIXELS, NEOPIXELPIN, NEO_GRB + NEO_KHZ800);

// Create a BLE UART service
BLEUart bleuart;

void setup() {
    // Put your code here to run once at setup

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

    // Start Accelerometer
    lsm6ds33.begin_I2C();
    // configure accelerometer for cough detection
    lsm6ds33.setAccelRange(LSM6DS_ACCEL_RANGE_4_G);
    lsm6ds33.setAccelDataRate(LSM6DS_RATE_104_HZ);
    // Initialize gravity filter with first reading
    sensors_event_t accel;
    sensors_event_t gyro;
    sensors_event_t temp;
    lsm6ds33.getEvent(&accel, &gyro, &temp);
    gravityX = accel.acceleration.x;
    gravityY = accel.acceleration.y;
    gravityZ = accel.acceleration.z;

    // Start sound sensor
    PDM.onReceive(onPDMdata);
    PDM.begin(1, 16000);

    //Initialize BLE
    Bluefruit.begin();
    Bluefruit.setName("AsthmaAlly-Chest");
    Bluefruit.setTxPower(-4);    // Increase power for better range, adjust as needed

    // Configure BLE UART service
    bleuart.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    bleuart.begin();

    // Start Advertising
    Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
    Bluefruit.Advertising.addName();
    Bluefruit.Advertising.addService(bleuart);

// Set advertising parameters
    Bluefruit.Advertising.restartOnDisconnect(true);
    Bluefruit.Advertising.setInterval(32, 244);    // in unit of 0.625 ms
    Bluefruit.Advertising.setFastTimeout(30);      // number of seconds in fast mode
    Bluefruit.Advertising.start();                // 0 = Don't stop advertising after n seconds
}

void loop() {
    // Put your code here to loop

    // indicate battery level
    VBat =  getBattVoltage(analogRead(VBATPIN)); 
    VBatIndicator();

    // Get sound sensor data
    PDMsamplesRead = 0;
    mic = getPDMwave(256);

    // Get Jerk movement data
    float acceleration = getFilteredAcceleration();

    // Compute potential diff across wheat bridge
    float PotDiff = getPotDiffWheat();
    int resistance = (680 * PotDiff + 1122) / (1.65 - PotDiff); // Convert voltage to resistance using Wheatstone bridge formula

    // Get internal noise data
    int32_t ElectretData = getElectretWave();
    if (ElectretData < 30) // filter noises too loud to be internal sounds
    {
      intmic = ElectretData;
    }

    // Prepare the data string to send
    String dataString = "";

    dataString += String(mic) + ",";
    dataString += String(ElectretData) + ",";
    dataString += String(acceleration) + ",";
    dataString += String(resistance) + "," +"\n";

    // Send data via Serial Monitor
    Serial.println(dataString);

    // Send data via BLE UART if connected
    if (bleuart.notifyEnabled()) {
      sendBLEData(dataString);
      delay(10); // Small delay to ensure data is sent properly
    }
}

// Put your function definitions here

// calculate RMS amplitude from samples
int32_t getPDMwave(int32_t samples) {
    long sum = 0;
    int count = 0;
    const int16_t AMPLITUDE_THRESHOLD = 10;  // Adjust this threshold based on testing to eliminate noise
    int16_t max_amplitude = 0;
    int16_t min_amplitude = 0;

    while (samples > 0) {
        if (!PDMsamplesRead) {
            yield();
            continue;
        }
        for (int i = 0; i < PDMsamplesRead; i++) {
            // Only process samples above noise threshold
            if (abs(PDMsampleBuffer[i]) > AMPLITUDE_THRESHOLD) {
                // Track min/max for peak-to-peak calculation
                if (PDMsampleBuffer[i] > max_amplitude) max_amplitude = PDMsampleBuffer[i];
                if (PDMsampleBuffer[i] < min_amplitude) min_amplitude = PDMsampleBuffer[i];
                
                // Square each sample and add to sum for RMS
                sum += (long)PDMsampleBuffer[i] * PDMsampleBuffer[i];
                count++;
            }
            samples--;
        }
        PDMsamplesRead = 0;
    }

    // Only return RMS if we have significant signal
    int32_t peak_to_peak = max_amplitude - min_amplitude;
    return peak_to_peak;
    // if (peak_to_peak > AMPLITUDE_THRESHOLD * 2) {
    //     return (count > 0) ? sqrt(sum / count) : 0;
    // }
    // return 0;  // Return 0 if signal is too weak
}

void onPDMdata() { // initialise PDM data reading
  // query the number of bytes available
  int bytesAvailable = PDM.available();

  // read into the sample buffer
  PDM.read(PDMsampleBuffer, bytesAvailable);

  // 16-bit, 2 bytes per sample
  PDMsamplesRead = bytesAvailable / 2;
}

float getBattVoltage(int x) { 
  float voltage = (x * 2 * 3.6) / 1024.0; 
  return constrain(voltage, 0.0, 4.2);  // LiPo max voltage is 4.2V
}

void VBatIndicator() { // alter neopixel colour to show battery status
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


float getFilteredAcceleration() { // Filter out gravity from accelerometer and obtain aggregated acceleration across all 3 axes
    sensors_event_t accel;
    sensors_event_t gyro;
    sensors_event_t temp;
    lsm6ds33.getEvent(&accel, &gyro, &temp);
    
    // Low-pass filter to extract gravity components for all axes
    gravityX = ALPHA * gravityX + (1.0 - ALPHA) * accel.acceleration.x;
    gravityY = ALPHA * gravityY + (1.0 - ALPHA) * accel.acceleration.y;
    gravityZ = ALPHA * gravityZ + (1.0 - ALPHA) * accel.acceleration.z;
    
    // High-pass filter to get dynamic acceleration
    float dynamicAccelX = accel.acceleration.x - gravityX;
    float dynamicAccelY = accel.acceleration.y - gravityY;
    float dynamicAccelZ = accel.acceleration.z - gravityZ;
    
    // Calculate total magnitude of acceleration change vector
    float totalDelta = sqrt(
        pow(dynamicAccelX - lastAccelX, 2) +
        pow(dynamicAccelY - lastAccelY, 2) +
        pow(dynamicAccelZ - lastAccelZ, 2)
    );
    
    // Update last readings
    lastAccelX = dynamicAccelX;
    lastAccelY = dynamicAccelY;
    lastAccelZ = dynamicAccelZ;
    
    return totalDelta;  // Return magnitude of acceleration change
}

float getPotDiffWheat() { // calculate the potential difference across two point on wheatstone bridge
    const int SAMPLES = 10;  // Number of samples to average
    float sumA = 0;
    float sumB = 0;
    
    // Take multiple readings
    for(int i = 0; i < SAMPLES; i++) {
        sumA += analogRead(WHEATBRIDGEPIN_A);
        sumB += analogRead(WHEATBRIDGEPIN_B);
        delay(1);  // Short delay between readings
    }
    
    // Calculate average and convert to voltage
    float voltageA = (sumA / SAMPLES * 3.3) / 1024.0;
    float voltageB = (sumB / SAMPLES * 3.3) / 1024.0;
    
    return fabsf(voltageA - voltageB);
}

int32_t getElectretWave() {
    const int sampleWindow = 50;  // Sample window width in mS (50 mS = 20Hz)
    unsigned int sample;
    unsigned long startMillis = millis(); // Start of sample window
    unsigned int peakToPeak = 0;   // peak-to-peak level

    unsigned int signalMax = 0;
    unsigned int signalMin = 1024;
    while (millis() - startMillis < sampleWindow) {
      sample = analogRead(ELECTRETPIN);
      if (sample < 1024)  // toss out spurious readings
      {
        if (sample > signalMax)
        {
          signalMax = sample;  // save just the max levels
        }
        else if (sample < signalMin)
        {
          signalMin = sample;  // save just the min levels
        }
      }
    }
    peakToPeak = signalMax - signalMin;
    return peakToPeak;
}

void sendBLEData(const String& data) {
    const int chunkSize = 20;  // BLE chunk size limit
    int dataLength = data.length();
    int position = 0;

    while (position < dataLength) {
        // Calculate remaining bytes and chunk length
        int remaining = dataLength - position;
        int currentChunkSize = (remaining > chunkSize) ? chunkSize : remaining;
        
        // Extract substring for this chunk
        String chunk = data.substring(position, position + currentChunkSize);
        
        // Send chunk
        bleuart.print(chunk);
        
        // Move to next position
        position += currentChunkSize;
        
        // Small delay between chunks to prevent data loss
        delay(5);
    }
    bleuart.println();   // Send newline to indicate end of data
    delay(10);  // Small delay to ensure data is sent properly
}