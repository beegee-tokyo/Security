/**
   setup()

   Initialization of GPIO pins, WiFi connection, timers and sensors

   @author Bernd Giesecke
   @version 0.1 beta December 2, 2015.
*/

void setup() {
  pinMode(detLED, OUTPUT); // Detection LED red
  pinMode(comLED, OUTPUT); // Communication LED blue
  pinMode(pirPort, INPUT); // PIR signal
  pinMode(relayPort, OUTPUT); // Relay trigger signal
  digitalWrite(detLED, HIGH); // Turn off LED
  digitalWrite(comLED, HIGH); // Turn off LED
  digitalWrite(relayPort, LOW); // Turn off Relay

  Serial.begin(115200);
  Serial.println("");
  Serial.println("Hello from EXP8266");

  WiFi.mode(WIFI_STA);
  WiFi.config(ipAddr, ipGateWay, ipSubNet);
  WiFi.begin(ssid, password);
  Serial.print("Waiting for WiFi connection ");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.print("Connected to ");
  Serial.println(ssid);
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  byte mac[6];
  WiFi.macAddress(mac);
  localMac = String(mac[0], HEX) + ":";
  localMac += String(mac[1], HEX) + ":";
  localMac += String(mac[2], HEX) + ":";
  localMac += String(mac[3], HEX) + ":";
  localMac += String(mac[4], HEX) + ":";
  localMac += String(mac[5], HEX);
  
  Serial.print("Sketch size: ");
  Serial.print (ESP.getSketchSize());
  Serial.print(" - Free size: ");
  Serial.println(ESP.getFreeSketchSpace());

  /* Configure the Adafruit TSL2561 light sensor */
  /* Set SDA and SCL pin numbers */
  tsl.setI2C(sdaPin, sclPin);
  /* Initialise the sensor */
  if ( tsl.begin() ) {
    /* Setup the sensor gain and integration time */
    configureSensor();
  }

  // Get hour
  getTime();
  sendAlarm();
  // Send Up message
  sendAlarm();
  // Start update of hour every 30 minutes ( 30x60=900 seconds)
  updateHourTimer.attach(900, triggerGetTime);
  // Start update of light value every 30 seconds
  getLightTimer.attach(30, triggerGetLight);
  // Initialize interrupt for PIR signal
  attachInterrupt(pirPort, pirTrigger, CHANGE);
  // Start UDP client for sending broadcasts
  udpClientServer.begin(5000);
}

