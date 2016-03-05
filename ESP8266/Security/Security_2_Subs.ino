/**
 * Change status of red led on each call
 * called by Ticker ledFlasher
 */
void redLedFlash() {
	int state = digitalRead(alarmLED);
	digitalWrite(alarmLED, !state);
}

/**
 * Change status of blue led on each call
 * called by Ticker comFlasher
 */
void blueLedFlash() {
	int state = digitalRead(comLED);
	digitalWrite(comLED, !state);
}

/**
 * Sets flag timeUpdateTriggered to true for handling in loop()
 * called by Ticker updateHourTimer
 * will initiate a call to getTime() from loop()
 */
// void triggerGetTime() {
	// timeUpdateTriggered = true;
// }

// /**
 // * Sets flag lightUpdateTriggered to true for handling in loop()
 // * called by Ticker updateLightTimer
 // * will initiate a call to getLight() from loop()
 // */
// void triggerGetLight() {
	// lightUpdateTriggered = true;
// }

/**
 * Sets flag lightLDRTriggered to true for handling in loop()
 * called by Ticker updateLDRTimer
 * will initiate a call to getLDR() from loop()
 */
void triggerGetLDR() {
	lightLDRTriggered = true;
}

/**
 * Reads analog input where LDR is connected
 * sets flag switchLights if value is lower than 850
 *
 * @return <code>boolean</code>
 *		true if status changed
 *		false if status is the same	
 */
boolean getLDR() {
	/** Flag for light status change */
	boolean hasChanged = false;
	 // Check light only if relay is off and light is switched off
	if (digitalRead(relayPort) == LOW) {
		ldrValue = (analogRead(A0));
		if (ldrValue < 850) {
			if (switchLights == false) { // On change send status 
				hasChanged = true;
			}
			switchLights = true;
		} else {
			if (switchLights == true) { // On change send status 
				hasChanged = true;
			}
			switchLights = false;
		}
	}
	return hasChanged;
}

/**
 * Return signal strength or 0 if target SSID not found
 * calls gcmSendOut to forward the request to the GCM server
 *
 * @return <code>int32_t</code>
 *              Signal strength as unsinged int or 0
 */
int32_t getRSSI() {
	/** Number of retries */
	byte retryNum = 0;
	/** Number of available networks */
	byte available_networks;
	/** The SSID we are connected to */
	String target_ssid(ssid);

	while (retryNum <= 3) {
		retryNum++;
		available_networks= WiFi.scanNetworks();
		if (available_networks == 0) { // Retryone time
			available_networks = WiFi.scanNetworks();
		}

		for (int network = 0; network < available_networks; network++) {
			if (WiFi.SSID(network).equals(target_ssid)) {
				return WiFi.RSSI(network);
			}
		}
	}
	return 0;
}

/**
 * Counts up until offDelay reaches onTime, then
 * switch off the relay
 * turn off the alarm sound
 * called by relayOffTimer
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
 * Create status JSON object
 *
 * @param root
 *              Json object to be filled with the status
 */
void createStatus(JsonObject& root) {
	// Create status
	// structure is:
	// {"device":DEVICE_ID,"alarm":0/1,"alarm_on":0/1,"light_on":0/1,"boot":0/1,"light_val":0..65536,"ldr_val":0..1024,"rssi":-100...+100,"reboot":rebootReason}
	// {"device":"sf1","alarm":1,"alarm_on":1,"light_on":0,"boot":1,"light_val":18652,"ldr_val":754,"rssi":-73,"reboot":"Lost connection"}
	root["device"] = DEVICE_ID;
	if (hasDetection) {
		root["alarm"] = 1;
	} else {
		root["alarm"] = 0;
	}
	if (alarmOn) {
		root["alarm_on"] = 1;
	} else {
		root["alarm_on"] = 0;
	}
	if (switchLights) {
		root["light_on"] = 1;
	} else {
		root["light_on"] = 0;
	}
	if (inSetup) {
		root["boot"] = 1;
	} else {
		root["boot"] = 0;
	}
	// root["light_val"] = lightValue;

	root["ldr_val"] = ldrValue;

	root["rssi"] = getRSSI();

	root["reboot"] = lastRebootReason;
}

/**
 * Write status to file
 *
 * @return <code>boolean</code>
 *              True if status was saved
 *              False if file error occured
 */
bool writeStatus() {
	// Open config file for writing.
	/** Pointer to file */
	File statusFile = SPIFFS.open("/status.txt", "w");
	if (!statusFile)
	{
		Serial.println("Failed to open status.txt for writing");
		return false;
	}
	// Create current status as JSON
	/** Buffer for Json object */
	DynamicJsonBuffer jsonBuffer;

	// Prepare json object for the response
	/* Json object with the status */
	JsonObject& root = jsonBuffer.createObject();

	// Create status
	createStatus(root);

	/** String in Json format with the status */
	String jsonTxt;
	root.printTo(jsonTxt);

	// Save status to file
	statusFile.println(jsonTxt);
	statusFile.close();
	return true;
}

/**
 * Write reboot reason to file
 *
 * @param message
 *              Reboot reason as string
 * @return <code>boolean</code>
 *              True if reboot reason was saved
 *              False if file error occured
 */
bool writeRebootReason(String message) {
	// Write current status to file
	writeStatus();
	// Now append reboot reason to file
	// Open config file for writing.
	/** Pointer to file */
	File statusFile = SPIFFS.open("/status.txt", "a");
	if (!statusFile)
	{
		Serial.println("Failed to open status.txt for writing");
		return false;
	}
	// Save reboot reason to file
	statusFile.println(message);
	statusFile.close();
	return true;
}

/**
 * Reads current status from status.txt
 * global variables are updated from the content
 *
 * @return <code>boolean</code>
 *              True if status could be read
 *              False if file error occured
 */
bool readStatus() {
	// open file for reading.
	/** Pointer to file */
	File statusFile = SPIFFS.open("/status.txt", "r");
	if (!statusFile)
	{
		Serial.println("Failed to open status.txt.");
		return false;
	}

	// Read content from config file.
	/** String with the status from the file */
	String content = statusFile.readString();
	statusFile.close();

	content.trim();

	// Check if there is a second line available.
	/** Index to end of first line in the string */
	int8_t pos = content.indexOf("\r\n");
	/** Index of start of secnd line */
	uint8_t le = 2;
	// check for linux and mac line ending.
	if (pos == -1)
	{
		le = 1;
		pos = content.indexOf("\n");
		if (pos == -1)
		{
			pos = content.indexOf("\r");
		}
	}

	// If there is no second line: Reboot reason is missing.
	if (pos != -1)
	{
		rebootReason = content.substring(pos + le);
	}

	// Create current status as from file as JSON
	/** Buffer for Json object */
	DynamicJsonBuffer jsonBuffer;

	// Prepare json object for the response
	/** String with content of first line of file */
	String jsonString = content.substring(0, pos);
	/** Json object with the last saved status */
	JsonObject& root = jsonBuffer.parseObject(jsonString);

	// Parse JSON
	if (!root.success())
	{
		// Parsing fail
		return false;
	}
	if (root.containsKey("alarm_on")) {
		if (root["alarm_on"] == 0) {
			alarmOn = false;
		} else {
			alarmOn = true;
		}
	}
}

/**
 * Connect to WiFi AP
 * if no WiFi is found for 60 seconds
 * module is restarted
 */
void connectWiFi() {
	comFlasher.attach(0.5, blueLedFlash);
	WiFi.disconnect();
	WiFi.mode(WIFI_STA);
	WiFi.config(ipAddr, ipGateWay, ipSubNet);
	WiFi.begin(ssid, password);
	Serial.print("Waiting for WiFi connection ");
	int connectTimeout = 0;
	while (WiFi.status() != WL_CONNECTED) {
		delay(500);
		Serial.print(".");
		connectTimeout++;
		if (connectTimeout > 60) { //Wait for 30 seconds (60 x 500 milliseconds) to reconnect
			// Safe reboot reason
			writeRebootReason("Lost connection");
			pinMode(16, OUTPUT); // Connected to RST pin
			digitalWrite(16,LOW); // Initiate reset
			ESP.reset();
		}
	}
	comFlasher.detach();
	digitalWrite(comLED, HIGH); // Turn off LED
}

/**
 * Called if there is a change in the WiFi connection
 *
 * @param event
 *              Event that happened
 */
void WiFiEvent(WiFiEvent_t event) {
	Serial.printf("[WiFi-event] event: %d\n", event);

	switch (event) {
		case WIFI_EVENT_STAMODE_CONNECTED:
			Serial.println("WiFi connected");
			break;
		case WIFI_EVENT_STAMODE_DISCONNECTED:
			Serial.println("WiFi lost connection");
			connectWiFi();
			break;
		case WIFI_EVENT_STAMODE_AUTHMODE_CHANGE:
			Serial.println("WiFi authentication mode changed");
			break;
		case WIFI_EVENT_STAMODE_GOT_IP:
			Serial.println("WiFi got IP");
			Serial.println("IP address: ");
			Serial.println(WiFi.localIP());
			break;
		case WIFI_EVENT_STAMODE_DHCP_TIMEOUT:
			Serial.println("WiFi DHCP timeout");
			break;
		case WIFI_EVENT_MAX:
			Serial.println("WiFi MAX event");
			break;
	}
}

/**
 * Send broadcast message over UDP into local network
 *
 * @param doGCM
 *              Flag if message is pushed over GCM as well
 */
void sendAlarm(boolean doGCM) {
	digitalWrite(comLED, LOW);
	/** Buffer for Json object */
	DynamicJsonBuffer jsonBuffer;

	// Prepare json object for the response
	/* Json object with the alarm message */
	JsonObject& root = jsonBuffer.createObject();

	// Create status
	createStatus(root);

	// Start UDP client for sending broadcasts
	udpClientServer.begin(5000);

	int connectionOK = udpClientServer.beginPacketMulticast(multiIP, 5000, ipAddr);
	if (connectionOK == 0) { // Problem occured!
		digitalWrite(comLED, HIGH);
		udpClientServer.stop();
		connectWiFi();
		return;
	}
	root.printTo(udpClientServer);
	udpClientServer.endPacket();
	udpClientServer.stop();

	if (doGCM) {
		/** Buffer for Json object */
		DynamicJsonBuffer msgBuffer;

		// Prepare json object for the response
		/** Json object with the push notification for GCM */
		JsonObject& msgJson = msgBuffer.createObject();
		msgJson["message"] = root;
		gcmSendMsg(msgJson);
	}
	digitalWrite(comLED, HIGH);
}

/**
 * Plays the tune defined with melody[] endless until ticker is detached
 */
void playAlarmSound() {
	/** Current tone to be played */
	int toneLength = melody[melodyPoint];
	analogWriteFreq(toneLength / 2);
	analogWrite(speakerPin, toneLength / 4);

	melodyPoint ++;
	if (melodyPoint == melodyLenght) {
		melodyPoint = 0;
	}
}

/**
 * Interrupt routine called if status of PIR detection status changes
 */
void pirTrigger() {
	Serial.println("Interrupt from PIR pin");
	if (digitalRead(pirPort) == HIGH) { // Detection of movement
		pirTriggered = true;
		hasDetection = true;
	} else { // No detection
		pirTriggered = true;
		hasDetection = false;
	}
}

/**
 * Triggered when push button is pushed
 * enables/disables alarm sound
 */
void buttonTrig() {
	// Get the pin reading.
	/** Value of button input */
	int reading = digitalRead(pushButton);

	// Ignore dupe readings.
	if (reading == state) return;

	/** Flag for debounce detection */
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
		buttonWasPressed = true;
		//		Serial.print("Alarm switched ");
		if (alarmOn && !hasDetection) {
			//		Serial.println("on");
			ledFlasher.attach(1, redLedFlash);
		} else {
			//		Serial.println("off");
			ledFlasher.detach();
			digitalWrite(alarmLED, HIGH);
		}
	}
}

/**
 * Connects to time server on address ipTime
 *	if hour is between 5pm and 7am
 *	- flag switchLights is set true (lights will go on if there is a detection)
 *	else
 *	- flag switchLights is set false (lights will not go on)
 */
// void getTime() {
	// digitalWrite(comLED, LOW);
	// /** Port for connection to server */
	// const int httpPort = 80;
	// if (!tcpClient.connect(ipTime, httpPort)) {
		// Serial.println("connection to time server " + String(ipAddr[0]) + "." + String(ipAddr[1]) + "." + String(ipAddr[2]) + "." + String(ipAddr[3]) + " failed");
		// digitalWrite(comLED, HIGH);
		// tcpClient.stop();
		// connectWiFi();
		// return;
	// }
	// tcpClient.print("GET /sd/spMonitor/date.php HTTP/1.0\r\n\r\n");

	// // Read all the lines of the reply from server and print them to Serial
	// /** String for the return value from the time server */
	// String line = "";
	// while (tcpClient.connected()) {
		// line = tcpClient.readStringUntil('\r');
	// }
	// Serial.print ("Hour is " + line.substring(8, 10) + " - ");
	// int timeNow = line.substring(8, 10).toInt();
	// if (timeNow <= 7 || timeNow >= 17) {
		// if (switchLights == false) {
			// sendAlarm(true);
		// }
		// switchLights = true;
		// Serial.println("We will switch on the light");
	// } else {
		// if (switchLights == true) {
			// sendAlarm(true);
		// }
		// switchLights = false;
		// Serial.println("We leave the light off");
	// }
	// tcpClient.stop();
	// digitalWrite(comLED, HIGH);
// }

/**
 * Answer request on http server
 * send last measured light value to requester
 *
 * @param httpClient
 *              Connected WiFi client
 */
void replyClient(WiFiClient httpClient) {
	/** String for response to client */
	String s = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json\r\n\r\n";
	/** Wait out time for client request */
	int waitTimeOut = 0;

	/** Buffer for Json object */
	DynamicJsonBuffer jsonBuffer;
	
	// Prepare json object for the response
	/** Json object for the response to the client */
	JsonObject& root = jsonBuffer.createObject();
	root["device"] = DEVICE_ID;

	// Wait until the client sends some data
	while (!httpClient.available()) {
		delay(1);
		waitTimeOut++;
		if (waitTimeOut > 3000) { // If no response for 3 seconds return
			root["result"] = "timeout";
			String jsonString;
			root.printTo(jsonString);
			s += jsonString;
			httpClient.print(s);
			httpClient.flush();
			httpClient.stop();
			connectWiFi();
			return;
		}
	}

	// Read the first line of the request
	/** String with the client request */
	String req = httpClient.readStringUntil('\r');
	// Strip leading (GET, PUSH) and trailing (HTTP/1) characters
	req = req.substring(req.indexOf("/"),req.length()-9);
	// String for response
	/** String to hold the response */
	String jsonString;

	// Switch on/off the alarm
	if (req.substring(0, 4) == "/?a=") {
		root["result"] = "success";
		if (req.substring(4, 5) == "0") {
			alarmOn = false;
			ledFlasher.detach();
			digitalWrite(alarmLED, HIGH);
		} else {
			alarmOn = true;
			ledFlasher.attach(1, redLedFlash);
		}
		createStatus(root);
		root.printTo(jsonString);
		s += jsonString;
		httpClient.print(s);
		httpClient.flush();
		httpClient.stop();
		delay(500);
		sendAlarm(false);
		return;
		// Request status
	} else if (req.substring(0, 3) == "/?s") {
	
		// Create status
		createStatus(root);

		root["ssid"] = String(ssid);
		root["ip"] = WiFi.localIP().toString();

		/** Byte array for the local MAC address */
		byte mac[6];
		WiFi.macAddress(mac);
		localMac = String(mac[0], HEX) + ":";
		localMac += String(mac[1], HEX) + ":";
		localMac += String(mac[2], HEX) + ":";
		localMac += String(mac[3], HEX) + ":";
		localMac += String(mac[4], HEX) + ":";
		localMac += String(mac[5], HEX);

		root["mac"] = localMac;

		root["sketch"] = String(ESP.getSketchSize());
		root["freemem"] = String(ESP.getFreeSketchSpace());

		root.printTo(jsonString);
		s += jsonString;
		httpClient.print(s);
		httpClient.flush();
		httpClient.stop();
		delay(1000);
		return;
		// Registration of new device
	} else if (req.substring(0, 8) == "/?regid=") {
		/** String to hold the received registration ID */
		String regID = req.substring(8,req.length());
		#ifdef DEBUG_OUT Serial.println("RegID: "+regID);
		Serial.println("Length: "+String(regID.length()));
		#endif
		// Check if length of ID is correct
		if (regID.length() != 140) {
			#ifdef DEBUG_OUT 
			Serial.println("Length of ID is wrong");
			#endif
			root["result"] = "invalid";
			root["reason"] = "Length of ID is wrong";
		} else {
			// Try to save ID 
			if (!addRegisteredDevice(regID)) {
				#ifdef DEBUG_OUT 
				Serial.println("Failed to save ID");
				#endif
				root["result"] = "failed";
				root["reason"] = failReason;
			} else {
				#ifdef DEBUG_OUT 
				Serial.println("Successful saved ID");
				#endif
				root["result"] = "success";
				getRegisteredDevices();
				for (int i=0; i<regDevNum; i++) {
					root[String(i)] = regAndroidIds[i];
				}
				root["num"] = regDevNum;
			}
		}
		root.printTo(jsonString);
		s += jsonString;
		httpClient.print(s);
		httpClient.flush();
		httpClient.stop();
		delay(1000);
		return;
	// Send list of registered devices
	} else if (req.substring(0, 3) == "/?l"){
		if (getRegisteredDevices()) {
			if (regDevNum != 0) { // Any devices already registered?
				for (int i=0; i<regDevNum; i++) {
					root[String(i)] = regAndroidIds[i];
				}
			}
			root["num"] = regDevNum;
			root["result"] = "success";
		} else {
			root["result"] = "failed";
			root["reason"] = failReason;
		}
		root.printTo(jsonString);
		s += jsonString;
		httpClient.print(s);
		httpClient.flush();
		httpClient.stop();
		delay(1000);
		return;
	// Delete one or all registered device
	} else if (req.substring(0, 3) == "/?d"){
		/** String for the sub command */
		String delReq = req.substring(3,4);
		if (delReq == "a") { // Delete all registered devices
			if (delRegisteredDevice(true)) {
				root["result"] = "success";
			} else {
				root["result"] = "failed";
				root["reason"] = failReason;
			}
		} else if (delReq == "i") {
			/** String to hold the ID that should be deleted */
			String delRegId = req.substring(5,146);
			delRegId.trim();
			if (delRegisteredDevice(delRegId)) {
				root["result"] = "success";
			} else {
				root["result"] = "failed";
				root["reason"] = failReason;
			}
		} else if (delReq == "x") {
			/** Index of the registration ID that should be deleted */
			int delRegIndex = req.substring(5,req.length()).toInt();
			if ((delRegIndex < 0) || (delRegIndex > MAX_DEVICE_NUM-1)) {
				root["result"] = "invalid";
				root["reason"] = "Index out of range";
			} else {
				if (delRegisteredDevice(delRegIndex)) {
					root["result"] = "success";
				} else {
					root["result"] = "failed";
					root["reason"] = failReason;
				}
			}
		}
		// Send list of registered devices
		if (getRegisteredDevices()) {
			if (regDevNum != 0) { // Any devices already registered?
				for (int i=0; i<regDevNum; i++) {
					root[String(i)] = regAndroidIds[i];
				}
			}
			root["num"] = regDevNum;
		}
		root.printTo(jsonString);
		s += jsonString;
		httpClient.print(s);
		httpClient.flush();
		httpClient.stop();
		delay(1000);
		return;
	}
	httpClient.flush();

	// Prepare the response
	s = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<!DOCTYPE HTML>\r\n<html>\r\n,";
	s += String(lightValue);
	s += ",</html>\r\n";

	// Send the response to the client
	httpClient.print(s);
	httpClient.stop();
}

