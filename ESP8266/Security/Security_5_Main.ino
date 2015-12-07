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
    sendAlarm();
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
    sendLight(client);
  }
}


