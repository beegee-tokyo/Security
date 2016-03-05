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

	// wdt_reset();
	// // Check if button was pressed
	// if (digitalRead(pushButton) == HIGH) { // button pushed (or electric spike on the line?)
		// if (!hasDetection) { // If detection is on ignore any button push (EMI problem)
			// delay(100); // Sit around for 100ms and then check again
			// if (digitalRead(pushButton) == HIGH) { // still high? Then maybe a real push and not a spike
				// alarmOn = !alarmOn;
				// //		Serial.print("Alarm switched ");
				// if (alarmOn && !hasDetection) {
					// //		Serial.println("on");
					// ledFlasher.attach(1, redLedFlash);
				// } else {
					// //		Serial.println("off");
					// ledFlasher.detach();
					// digitalWrite(alarmLED, HIGH);
				// }
				// sendAlarm(false);
			// }
		// }
	// }
	
	// wdt_reset();
	// // Handle new time update request
	// if (timeUpdateTriggered) {
		// timeUpdateTriggered = false;
		// getTime();
	// }

	// wdt_reset();
	// // Handle new light update request
	// if (lightUpdateTriggered) {
		// lightUpdateTriggered = false;
		// getLight();
	// }

	wdt_reset();
	// Handle new LDR update request
	if (lightLDRTriggered) {
		lightLDRTriggered = false;
		if (getLDR()) {
			sendAlarm(false);
		}
	}

	wdt_reset();
	// Handle new client request on HTTP server if available
	WiFiClient client = server.available();
	if (client) {
		digitalWrite(comLED, LOW);
		replyClient(client);
		digitalWrite(comLED, HIGH);
	}

	wdt_reset();
	// Handle OTA updates
	ArduinoOTA.handle();
	
	// wdt_reset();
	// // Give a "I am alive" signal
	// liveCnt++;
	// if (liveCnt == 50000) {
		// blueLedFlash();
		// liveCnt = 0;
	// }
	
}


