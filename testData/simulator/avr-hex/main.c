/* ATmega328P at 16 MHz: emit a marker through UART0 only after flash executes. */
#include <avr/io.h>
#include <util/delay.h>

/* Write a NUL-terminated string through UART0, waiting for each transmit slot. */
static void print(const char *text) {
    while (*text) {
        while (!(UCSR0A & _BV(UDRE0))) {}
        UDR0 = *text++;
    }
}

/* Configure UART0 and repeatedly emit the golden marker from executing firmware. */
int main(void) {
    UBRR0H = 0;
    UBRR0L = 103; /* 9600 baud, normal speed, 16 MHz clock */
    UCSR0A = 0;
    UCSR0B = _BV(TXEN0);
    UCSR0C = _BV(UCSZ01) | _BV(UCSZ00); /* 8N1 */
    for (;;) {
        print("HEX simulation ready\n");
        _delay_ms(1000);
    }
}
