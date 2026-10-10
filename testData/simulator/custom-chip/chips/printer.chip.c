#include <stdio.h>

/* The two Wokwi entry points from wokwi-api.h; this chip uses no peripheral APIs. */
__attribute__((export_name("__wokwi_api_version_1")))
int wokwi_api_version(void) {
    return 1;
}

/* Called by Wokwi when it instantiates the chip, never by the AVR firmware. */
__attribute__((export_name("chipInit")))
void chip_init(void) {
    printf("Custom chip simulation ready\n");
}
