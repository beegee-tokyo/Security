/**
 * Security Monitor
 * 
 * Hardware
 * Adafruit HUZZAH ESP8266 (ESP-12) module - https://www.adafruit.com/products/2471
 * Adafruit PIR (motion) sensor - https://www.adafruit.com/products/189
 * Adafruit TSL2561 Digital Luminosity/Lux/Light Sensor - http://www.adafruit.com/products/439
 * dfRobot Relay Modular V3.1 (10A/220V max) - http://www.dfrobot.com/index.php?route=product/product&product_id=64#.VmBYpXYrJpg
 * 
 * Uses PIR sensor to detect measurement.
 * Relay to switch on external light.
 * Light on time 30 seconds, extended if PIR sensor is retriggered
 * Light on only between 5pm and 7am (time info from Arduino Yun running 24h to monitor my solar panels)
 * Playing an alarm sound if enabled
 * Collecting light values for
 * - Light on decision (not implemented yet, will replace time driven light on decsion)
 * - Light information made available for solar panel monitor (not implemented yet)
 *
 * @author Bernd Giesecke
 * @version 0.1 beta December 2, 2015.
 */

/* Includes from libraties */
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <Ticker.h>
#include <Timer.h>
#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_TSL2561_U_ESP.h>
#include <pgmspace.h>


/* wifiAPinfo.h contains wifi SSID and password */
/* file content looks like: */
/* Begin of file:
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";
   End of file */
#include "wifiAPinfo.h"

/** Red LED on GPIO0 for visual signal if alarm is on or off */
#define alarmLED 0
/** Blue LED on GPIO2 for communication activities */
#define comLED 2
/** Input from PIR sensor */
#define pirPort 4
/** Output to activate Relay */
#define relayPort 5
/** Data pin for I2C communication with light sensor */
#define sdaPin 13
/** Clock pin for I2C communication with light sensor */
#define sclPin 12
/** Output to loudspeaker or piezo */
#define speakerPin 15
/** Input from push button */
#define pushButton 14

/** WiFiClient class to create TCP communication */
WiFiClient tcpClient;

/** WiFiUDP class for creating UDP communication */
WiFiUDP udpClientServer;

/** WiFiServer class to create simple web server */
WiFiServer server(80);

/** IP address of this module */
IPAddress ipAddr(192, 168, 0, 141);
/** Gateway address of WiFi access point */
IPAddress ipGateWay(192, 168, 0, 1);
/** Network mask of the local lan */
IPAddress ipSubNet(255, 255, 255, 0);
/** Network address of time server (Arduino Yun) */
IPAddress ipTime(192, 168, 0, 140);
/** Network address mask for UDP multicast messaging */
IPAddress multiIP (192,  168, 0, 255);

/** MAC address of this module = unique id on the LAN */
String localMac = "";

/** Timer for flashing red detection LED */
Ticker ledFlasher;
/** Timer to switch off the relay */
Ticker relayOffTimer;
/** Timer to contact time server to get current hour for light on/off */
Ticker updateHourTimer;
/** Timer to collect light information from TSL2561 sensor */
Ticker getLightTimer;
/** Timer for alarm siren */
Ticker alarmTimer;

/** Flag for alarm activity */
boolean alarmOn = true;
/** Holds the current button state. */
volatile int state;
/** Holds the last time debounce was evaluated (in millis). */
volatile long lastDebounceTime = 0;
/** The delay threshold for debounce checking. */
const int debounceDelay = 50;

/** Melody as delay time */
//long melody[] = {1700,1700,1136,1136,1432,1915,1915,1700,1700,1136,1136,1700,1700,1915,1915,1432,1432,1700,1700,1136,1136,1915,1915,1700,1700,1136,1136,1432,1915,1915,1700,1700,1136,1136,1136,1136,1275,1275,1275,1275};
long melody[] = {1915,1915,1915,1915,1275,1275,1275,1275,1915,1915,1915,1915,1275,1275,1275,1275,1915,1915,1915,1915,1275,1275,1275,1275,1915,1915,1915,1915,1275,1275,1275,1275,1915,1915,1915,1915,1275,1275,1275,1275};

/** Relation between values and notes */
//{1915, 1700, 1519, 1432, 1275, 1136, 1014, 956};
//   c     d     e     f     g     a     b    c 

/** Melody position pointer */
int melodyPoint = 0;
/** Number of melody[] notes */
int melodyLenght = 40;
/** Time to play a single tune in milliseconds */
int melodyTuneTime = 175;

/** Relay on delay time in seconds */
int onTime = 30;
/** Counter for relay switch off timing */
long offDelay = 0;
/** Flag if lights should be switched on after movement detection */
boolean switchLights = false;
/** Flag for PIR status change */
boolean pirTriggered = false;
/** Flag for request to contact time server to get current hour for light on/off */
boolean timeUpdateTriggered = false;
/** Flag for request to read out light sensor */
boolean lightUpdateTriggered = false;

/** Flag for detection status */
boolean hasDetection = false;

/** Instance of the Adafruit TSL2561 sensor */
Adafruit_TSL2561_Unified tsl = Adafruit_TSL2561_Unified ( TSL2561_ADDR_FLOAT, 1 );
/** Currently used integration time for light sensor, 0 = 13.7ms, 1 = 101ms, 2 = 402ms */
int lightInteg = 2;
/** Result of last light measurement */
long lightValue = 0;

