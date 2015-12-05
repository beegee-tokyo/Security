/**
   Subroutines used by setup(), loop(), timers and interrupts

   @author Bernd Giesecke
   @version 0.1 beta December 2, 2015.
*/

/**
   ledFlash
   called by Ticker ledFlasher
   change status of led on each call
*/
void ledFlash() {
  int state = digitalRead(detLED);
  digitalWrite(detLED, !state);
}

/**
   relayOff
   called by relayOffTimer
   counts up until offDelay reaches onTime, then switch off the relay
*/
void relayOff() {
  offDelay += 1;
  if (offDelay == onTime) {
    digitalWrite(relayPort, LOW);
    relayOffTimer.detach();
  }
}

/**
   sendAlarm
   send broadcast message
   over UDP into local network
*/
void sendAlarm() {
  digitalWrite(comLED, LOW);
  
  // Create broadcast message
  // structure is:
  // module_name,mac_add,detection_status,light_value
  // ESP8266,cd:60:1:7f:cf:5c,0,35600
  
  String broadCast = "ESP8266," + localMac + ",";
  if (hasDetection) {
  	broadCast += "1,";
  } else {
  	broadCast += "0,";
  }
  broadCast += String(lightValue);
  Serial.println("Broadcast message: " + broadCast);

  int broadCastLen = broadCast.length();
  byte broadCastBuffer[broadCastLen];
  for (int i = 0; i < broadCastLen; i++) {
    broadCastBuffer[i] = broadCast[i];
  }

  udpClientServer.beginPacketMulticast(multiIP, 5000, ipAddr);
  udpClientServer.write(broadCastBuffer, broadCastLen);
  udpClientServer.endPacket();

  digitalWrite(comLED, HIGH);
}

/**
   pirTrigger
   interrupt routine called if status of PIR detection status changes
   if there is a detection
   - the detection led starts to flash
   - the relay is switched on (if flag switchLights is true)
   - msgText is set to detection message
   - flag pirTriggered is set true for handling in loop()
   if detection is finished
   - the detection led stops flashing
   - msgText is set to no detection message
   - flag pirTriggered is set true for handling in loop()
*/
void pirTrigger() {
  Serial.println("Interrupt from PIR pin");
  if (digitalRead(pirPort) == HIGH) { // Detection of movement
    ledFlasher.attach(0.2, ledFlash);
    relayOffTimer.detach();
    if (switchLights) {
      offDelay = 0;
      relayOffTimer.attach(1, relayOff);
      digitalWrite(relayPort, HIGH);
    } else {
      digitalWrite(relayPort, LOW);
    }
    pirTriggered = true;
    hasDetection = true;
  } else { // No detection
    ledFlasher.detach();
    digitalWrite(detLED, HIGH);
    pirTriggered = true;
    hasDetection = false;
  }
}

/**
   getTime
   connects to time server on address ipTime
   if hour is between 5pm and 7am
   - flag switchLights is set true (lights will go on if there is a detection)
   else
   - flag switchLights is set false (lights will not go on)
*/
void getTime() {
  digitalWrite(comLED, LOW);
  const int httpPort = 80;
  if (!tcpClient.connect(ipTime, httpPort)) {
    Serial.println("connection to time server " + String(ipAddr[0]) + "." + String(ipAddr[1]) + "." + String(ipAddr[2]) + "." + String(ipAddr[3]) + " failed");
    digitalWrite(comLED, HIGH);
    tcpClient.stop();
    return;
  }
  tcpClient.print("GET /sd/spMonitor/date.php HTTP/1.0\r\n\r\n");

  // Read all the lines of the reply from server and print them to Serial
  String line = "";
  while (tcpClient.connected()) {
    line = tcpClient.readStringUntil('\r');
  }
  Serial.print ("Hour is " + line.substring(8, 10) + " - ");
  int timeNow = line.substring(8, 10).toInt();
  if (timeNow <= 7 || timeNow >= 17) {
    switchLights = true;
    Serial.println("We will switch on the light");
  } else {
    switchLights = false;
    Serial.println("We leave the light off");
  }
  tcpClient.stop();
  digitalWrite(comLED, HIGH);
}

/**
   triggerGetTime
   called by Ticker updateHourTimer
   sets flag timeUpdateTriggered to true for handling in loop()
   will initiate a call to getTime() from loop()
*/
void triggerGetTime() {
  timeUpdateTriggered = true;
}

/**
   triggerGetTime
   called by Ticker updateHourTimer
   sets flag lightUpdateTriggered to true for handling in loop()
   will initiate a call to getLight() from loop()
*/
void triggerGetLight() {
  lightUpdateTriggered = true;
}

