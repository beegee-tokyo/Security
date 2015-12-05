# Security

Security system based on ESP8266 module with Android devices as monitoring system

Hardware
 * Adafruit HUZZAH ESP8266 (ESP-12) module - https://www.adafruit.com/products/2471
 * Adafruit PIR (motion) sensor - https://www.adafruit.com/products/189
 * Adafruit TSL2561 Digital Luminosity/Lux/Light Sensor - http://www.adafruit.com/products/439
 * dfRobot Relay Modular V3.1 (10A/220V max) - http://www.dfrobot.com/index.php?route=product/product&product_id=64#.VmBYpXYrJpg
 *
 * Uses PIR sensor to detect measurement.
 * Relay to switch on external light.
 * Light on time 30 seconds, extended if PIR sensor is retriggered
 * Light on only between 5pm and 7am (time info from Arduino Yun running 24h to monitor my solar panels)
 * Collecting light values for
 * - Light on decision (not implemented yet, will replace time driven light on decsion)
 * - Light information made available for solar panel monitor (not implemented yet)
 *
 * @author Bernd Giesecke
 * @version 0.1 beta December 2, 2015.
