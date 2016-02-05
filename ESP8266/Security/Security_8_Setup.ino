/**
 * Initialization of GPIO pins, WiFi connection, timers and sensors
 */
void setup() {
	pinMode(alarmLED, OUTPUT); // Detection LED red
	pinMode(comLED, OUTPUT); // Communication LED blue
	pinMode(pirPort, INPUT); // PIR signal
	pinMode(relayPort, OUTPUT); // Relay trigger signal
	pinMode(speakerPin, OUTPUT); // Loudspeaker/piezo signal
	pinMode(pushButton, INPUT); // Input from push button
	digitalWrite(alarmLED, HIGH); // Turn off LED
	digitalWrite(comLED, HIGH); // Turn off LED
	digitalWrite(relayPort, LOW); // Turn off Relay
	digitalWrite(speakerPin, LOW); // Speaker off

	Serial.begin(115200);
	Serial.setDebugOutput(false);
	Serial.println("");
	Serial.println("====================");
	Serial.println("ESP8266 Security");

	// Setup WiFi event handler
	WiFi.onEvent(WiFiEvent);
	
	connectWiFi();

	Serial.println("");
	Serial.print("Connected to ");
	Serial.println(ssid);
	Serial.print("IP address: ");
	Serial.println(WiFi.localIP());

	/** Byte array for the local MAC address */
	byte mac[6];
	WiFi.macAddress(mac);
	localMac = String(mac[0], HEX) + ":";
	localMac += String(mac[1], HEX) + ":";
	localMac += String(mac[2], HEX) + ":";
	localMac += String(mac[3], HEX) + ":";
	localMac += String(mac[4], HEX) + ":";
	localMac += String(mac[5], HEX);

	Serial.print("MAC address: ");
	Serial.println(localMac);

	Serial.print("Sketch size: ");
	Serial.print (ESP.getSketchSize());
	Serial.print(" - Free size: ");
	Serial.println(ESP.getFreeSketchSpace());
	Serial.println("====================");

	/* Configure the Adafruit TSL2561 light sensor */
	/* Set SDA and SCL pin numbers */
	//tsl.setI2C(sdaPin, sclPin);
	/* Initialise the sensor */
	//if ( tsl.begin() ) {
		/* Setup the sensor gain and integration time */
	//	configureSensor();
	//}

	// Get initial light
	// TODO light sensor not attached
	// for testing, the light value reading is disabled
	//getLight();
	// Start update of light value every 30 seconds
	//getLightTimer.attach(30, triggerGetLight);

	// Trying to replace this with the LDR measurement
	// Disabled for now
	// Get initial time
//	getTime();
	// Start update of hour every 30 minutes ( 30x60=900 seconds)
//	updateHourTimer.attach(900, triggerGetTime);

	// Start update of LDR value every 60 seconds
	getLDRTimer.attach(60, triggerGetLDR);
	// Get initial value from LDR 
	getLDR();
	
	// Initialize interrupt for PIR signal
	attachInterrupt(pirPort, pirTrigger, CHANGE);

	// Initialize interrupt for push button signal
	// TODO check hardware for connections between PIR and button input
	// PIR alert triggers a button push as well (but only once)
	// maybe it is EMV problem ???
	// For now the button is disabled
	attachInterrupt(pushButton, buttonTrig, CHANGE);

	// Initialize file system.
	if (!SPIFFS.begin())
	{
		Serial.println("Failed to mount file system");
		return;
	}

	// Try to get last status & last reboot reason from status.txt
	Serial.println("====================");
	if (!readStatus()) {
		Serial.println("No status file found");
		writeRebootReason("Unknown");
		lastRebootReason = "No status file found";
	} else {
		Serial.println("Last reboot because: " + rebootReason);
		lastRebootReason = rebootReason;
	}
	Serial.println("====================");

	// Send Security restart message
	sendAlarm(true);

	// Reset boot status flag
	inSetup = false;

	// Start the web server to serve incoming requests
	server.begin();

	if (alarmOn) {
		ledFlasher.attach(1, redLedFlash);
	} else {
		ledFlasher.detach();
		digitalWrite(alarmLED, HIGH); // Turn off LED
	}

	ArduinoOTA.onStart([]() {
		// Safe reboot reason
		writeRebootReason("OTA");

		// Detach all interrupts and timers
		wdt_disable();
		ledFlasher.attach(0.1, redLedFlash); // Flash very fast if we started update
		relayOffTimer.detach();
		updateHourTimer.detach();
		getLightTimer.detach();
		alarmTimer.detach();

		WiFiUDP::stopAll();
		WiFiClient::stopAll();
		server.close();
	});

	// Start OTA server.
	ArduinoOTA.setHostname(OTA_HOST);
	ArduinoOTA.begin();

	wdt_enable(WDTO_8S);
}

