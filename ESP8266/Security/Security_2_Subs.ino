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
  int state = digitalRead(alarmLED);
  digitalWrite(alarmLED, !state);
}

/**
   relayOff
   called by relayOffTimer
   counts up until offDelay reaches onTime, then
   switch off the relay
   turn off the alarm sound
*/
void relayOff() {
  offDelay += 1;
  if (offDelay == onTime) {
    digitalWrite(relayPort, LOW);
    relayOffTimer.detach();
    alarmTimer.detach();
    analogWrite(speakerPin, 0);
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
   toggleAlarmSound
   plays the tune defined with melody[] endless until ticker is detached
*/
void playAlarmSound() {
  int toneLength = melody[melodyPoint];
  analogWriteFreq(toneLength / 2);
  analogWrite(speakerPin, toneLength / 4);

  melodyPoint ++;
  if (melodyPoint == melodyLenght) {
    melodyPoint = 0;
  }
}

/**
   pirTrigger
   interrupt routine called if status of PIR detection status changes
   if there is a detection
   - the detection led starts to flash
   - the relay is switched on (if flag switchLights is true)
   - alarm sound is played (if flag switchLights is true)
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
    ledFlasher.attach(0.2, ledFlash); // Flash fast if we have a detection
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
    ledFlasher.detach(); // Stop flashing if we have no detection
    digitalWrite(alarmLED, HIGH);
    if (alarmOn) { // If alarm is active, continue to flash slowly
      ledFlasher.attach(0.4, ledFlash);
    }
    alarmTimer.detach();
    pirTriggered = true;
    hasDetection = false;
  }
}

/**
   buttonTrig
   Triggered when push button is pushed
   Enables/Disables alarm sound
*/
void buttonTrig() {
  // Get the pin reading.
  int reading = digitalRead(pushButton);

  // Ignore dupe readings.
  if (reading == state) return;

  boolean debounce = false;

  // Check to see if the change is within a debounce delay threshold.
  if ((millis() - lastDebounceTime) <= debounceDelay) {
    debounce = true;
  }

  // This update to the last debounce check is necessary regardless of debounce state.
  lastDebounceTime = millis();

  // Ignore reads within a debounce delay threshold.
  if (debounce) return;

  // All is good, persist the reading as the state.
  state = reading;

  if (reading == HIGH) {
    alarmOn = !alarmOn;
    Serial.print("Alarm switched ");
    if (alarmOn) {
      Serial.println("on");
      ledFlasher.attach(1, ledFlash);
    } else {
      Serial.println("off");
      ledFlasher.detach();
      digitalWrite(alarmLED, HIGH);
    }
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

/**
   sendLight
   answer request on http server
   send last measured light value to requester
*/
void sendLight(WiFiClient httpClient) {
  // Wait until the client sends some data
  while (!httpClient.available()) {
    delay(1);
  }

  // Read the first line of the request
  String req = httpClient.readStringUntil('\r');
  if (req.substring(4, 9) == "/?a=0") {
    alarmOn = false;
    httpClient.flush();
    httpClient.print("Alarm switched off");
    httpClient.stop();
    ledFlasher.detach();
    digitalWrite(alarmLED, HIGH);
    return;
  }
  if (req.substring(4, 9) == "/?a=1") {
    alarmOn = true;
    httpClient.flush();
    httpClient.print("Alarm switched on");
    httpClient.stop();
    ledFlasher.attach(1, ledFlash);
    return;
  }
  httpClient.flush();

  // Prepare the response
  String s = ""; //"HTTP / 1.1 200 OK\r\nContent - Type: text / html\r\n\r\n < !DOCTYPE HTML > \r\n<html>\r\n";
  s += String(lightValue);
  //s += " </html> \n";

  // Send the response to the client
  httpClient.print(s);
  httpClient.stop();
}

