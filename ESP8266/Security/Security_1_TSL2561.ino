/**************************************************************************/
/*
    Adafruit sensor routines taken from "sensorapi.pde"
*/
/**************************************************************************/
/**
   Configures the gain and integration time for the TSL2561
*/
void configureSensor () {
  /* You can also manually set the gain or enable auto-gain support */
  tsl.enableAutoRange ( true );         /* Auto-gain ... switches automatically between 1x and 16x */

  /* Changing the integration time gives you better sensor resolution (402ms = 16-bit data) */
  tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_402MS ); /* 16-bit data but slowest conversions */
}

/**
   Get current light measurement.
   Function makes 5 measurements and returns the average value.
   Function adapts integration time in case of sensor overload

   Result is stored in global variable sunLux
*/
void getLight () {
  /** Sensor event reads value from the sensor */
  sensors_event_t event;

  tsl.getEvent ( &event );

  lightValue = 0;
  for (int i = 0; i < 3; i++) { // do 3 runs, in case we get saturation
    /* Display the results (light is measured in lux) */
    if ( event.light ) {
      /** Int value read from AD conv for sun measurement */
      lightValue = event.light;
      Serial.println("Light result = " + String(lightValue) + " lux Integration = " + String(lightInteg));

      if ( lightInteg == 1 ) { /* we are at medium integration time, try a higher one */
        tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_402MS ); /* 16-bit data but slowest conversions */
        /* Test new integration time */
        tsl.getEvent ( &event );

        if ( event.light == 0 ) {
          /* Satured, switch back to medium integration time */
          tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_101MS ); /* medium resolution and speed   */
        } else {
          lightInteg = 2;
          Serial.println("Light result = " + String(lightValue) + " lux switch to Integration = " + String(lightInteg));
        }
      } else if ( lightInteg == 0 ) { /* we are at lowest integration time, try a higher one */
        tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_101MS ); /* medium resolution and speed   */
        /* Test new integration time */
        tsl.getEvent ( &event );

        if ( event.light == 0 ) {
          /* Satured, switch back to low integration time */
          tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_13MS ); /* fast but low resolution */
        } else {
          lightInteg = 1;
          Serial.println("Light result = " + String(lightValue) + " lux switch to Integration = " + String(lightInteg));
        }
      }
    } else {
      /* If event.light = 0 lux the sensor is probably saturated
                                               and no reliable data could be generated! */
      Serial.println("Light result = saturated Integration = " + String(lightInteg));
      if ( lightInteg == 2 ) { /* we are at highest integration time, try a lower one */
        tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_101MS ); /* medium resolution and speed   */
        tsl.getEvent ( &event );

        if ( event.light == 0 ) { /* Still saturated? */
          lightInteg = 0;
          tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_13MS ); /* fast but low resolution */
          tsl.getEvent ( &event );

          if ( event.light != 0 ) { /* Got a result now? */
            lightValue = event.light;
            Serial.println("Light result = " + String(lightValue) + " lux switch to Integration = " + String(lightInteg));
          } else {
            lightValue = 65535; // Still saturated at lowest integration time, assume max level of light
          }
        } else {
          Serial.println("Light result = saturated Integration = " + String(lightInteg));
          lightInteg = 1;
          lightValue = event.light;
        }
      } else if ( lightInteg == 1 ) { /* we are at medium integration time, try a lower one */
        lightInteg = 0;
        tsl.setIntegrationTime ( TSL2561_INTEGRATIONTIME_13MS ); /* fast but low resolution */
        tsl.getEvent ( &event );
        if ( event.light != 0 ) { /* Got a result now? */
          lightValue = event.light;
          Serial.println("Light result = " + String(lightValue) + " lux switch to Integration = " + String(lightInteg));
        } else {
          lightValue = 65535; // Still saturated at lowest integration time, assume max level of light
        }
      }
    }
  }
  String integTime = "";
  if ( lightInteg == 2 ) {
    integTime = "402ms";
  } else if (lightInteg == 1) {
    integTime = "101ms";
  } else {
    integTime = "13ms";
  }
}


