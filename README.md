# Security

Security system based on ESP8266 module with Android devices as monitoring system

Hardware
 * Adafruit HUZZAH ESP8266 (ESP-12) module - https://www.adafruit.com/products/2471
 * Adafruit PIR (motion) sensor - https://www.adafruit.com/products/189
 * Adafruit TSL2561 Digital Luminosity/Lux/Light Sensor - http://www.adafruit.com/products/439 (optional)
 * dfRobot Relay Modular V3.1 (10A/220V max) - http://www.dfrobot.com/index.php?route=product/product&product_id=64#.VmBYpXYrJpg
 * LDR resistor
 * push button
 * loud speaker
 * NPN transistor
 * diverse capacitors and resistors (see Fritzing file for details)
 
 * Uses PIR sensor to detect measurement.
 * Relay to switch on external light.
 * Light on time 2 minutes, extended if PIR sensor is retriggered
 * Light on depending on enviroment light measured with LDR resistor
 *
 * @author Bernd Giesecke
 * @version 0.2 beta March 05, 2016.
