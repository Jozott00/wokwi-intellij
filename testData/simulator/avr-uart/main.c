/* Shared HEX execution and source-level debugger fixture: ATmega328P, UART0 at 9600 baud. */
#include <avr/io.h>
#include <util/delay_basic.h>

static void print(const char *text) {
    while (*text) {
        while (!(UCSR0A & _BV(UDRE0))) {}
        UDR0 = *text++;
    }
}

int main(void) {
    UBRR0H = 0;
    UBRR0L = 103; /* 9600 baud, normal speed, 16 MHz clock */
    UCSR0A = 0;
    UCSR0B = _BV(TXEN0);
    UCSR0C = _BV(UCSZ01) | _BV(UCSZ00); /* 8N1 */
    volatile unsigned int counter = 41;
    counter += 1; /* DEBUG_BREAKPOINT */
    counter += 1; /* DEBUG_STEP */
    for (;;) {
        print("AVR simulation ready\n");
        /* About one second at 16 MHz; delay_basic works with unoptimized debug builds. */
        for (unsigned int tick = 0; tick < 1000; ++tick) {
            _delay_loop_2(4000);
        }
    }
}
