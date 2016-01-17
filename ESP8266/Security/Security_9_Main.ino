/**
	 loop()

	 Handling of GPIO and timer interrupts

	 @author Bernd Giesecke
	 @version 0.1 beta December 2, 2015.
*/

void loop() {
	// Handle new PIR status if available
	if (pirTriggered) {
		pirTriggered = false;
		Serial.println("Interrupt from PIR pin");
		if (hasDetection) { // Detection of movement
			ledFlasher.attach(0.2, ledFlash); // Flash fast if we have a detection
			relayOffTimer.detach();
			if (switchLights) {
				offDelay = 0;
				relayOffTimer.attach(1, relayOff);
				digitalWrite(relayPort, HIGH);
			} else {
				digitalWrite(relayPort, LOW);
			}
			if (alarmOn) {
				melodyPoint = 0; // Reset melody pointer to 0
				alarmTimer.attach_ms(melodyTuneTime, playAlarmSound);
				sendAlarm();
			}
		} else { // No detection
			ledFlasher.detach(); // Stop fast flashing if we have no detection
			alarmTimer.detach();
			analogWrite(speakerPin, LOW); // Switch off speaker
			digitalWrite(alarmLED, HIGH);
			if (alarmOn) { // If alarm is active, continue to flash slowly
				ledFlasher.attach(0.4, ledFlash);
				sendAlarm();
			}
		}
	}

	// Handle new time update request
	if (timeUpdateTriggered) {
		timeUpdateTriggered = false;
		getTime();
	}

	// Handle new light update request
	if (lightUpdateTriggered) {
		lightUpdateTriggered = false;
		getLight();
	}

	// Handle new client request on HTTP server if available
	WiFiClient client = server.available();
	if (client) {
		replyClient(client);
	}

	ArduinoOTA.handle();
}


