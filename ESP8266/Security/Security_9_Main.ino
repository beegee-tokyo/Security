/**
 * Main loop
 * Processing of the result of GPIO and timer interrupts
 * Calling replyClient if a web client is contacting
 */
void loop() {
	wdt_reset();
	/* Handle new PIR status if available
	*	if there is a detection
	*	- the detection led starts to flash
	*	- the relay is switched on (if flag switchLights is true)
	*	- alarm sound is played (if flag switchLights is true)
	*	- msgText is set to detection message
	*	- flag pirTriggered is set true for handling in loop()
	*	if detection is finished
	*	- the detection led stops flashing
	*	- msgText is set to no detection message
	*	- flag pirTriggered is set true for handling in loop()
	*/

	if (pirTriggered) {
		pirTriggered = false;
		Serial.println("Interrupt from PIR pin");
		if (hasDetection) { // Detection of movement
			ledFlasher.attach(0.2, redLedFlash); // Flash fast if we have a detection
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
				sendAlarm(true);
			}
		} else { // No detection
			ledFlasher.detach(); // Stop fast flashing if we have no detection
			alarmTimer.detach();
			analogWrite(speakerPin, LOW); // Switch off speaker
			digitalWrite(alarmLED, HIGH);
			if (alarmOn) { // If alarm is active, continue to flash slowly
				ledFlasher.attach(0.4, redLedFlash);
				sendAlarm(true);
			}
		}
	}

	wdt_reset();
	// Send status update if button was pressed
	if (buttonWasPressed) {
		buttonWasPressed = false;
		sendAlarm(false);
	}

	wdt_reset();
	// Handle new time update request
	if (timeUpdateTriggered) {
		timeUpdateTriggered = false;
		getTime();
	}

	wdt_reset();
	// Handle new light update request
	if (lightUpdateTriggered) {
		lightUpdateTriggered = false;
		getLight();
	}

	wdt_reset();
	// Handle new LDR update request
	if (lightLDRTriggered) {
		lightLDRTriggered = false;
		getLDR();
	}

	wdt_reset();
	// Handle new client request on HTTP server if available
	WiFiClient client = server.available();
	if (client) {
		replyClient(client);
	}

	wdt_reset();
	ArduinoOTA.handle();
}


