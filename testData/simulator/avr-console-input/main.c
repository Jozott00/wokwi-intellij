/* ATmega328P UART0 at 9600 baud: respond only after a complete input line arrives. */
#include <avr/io.h>

static void print(const char *text) {
    while (*text) {
        while (!(UCSR0A & _BV(UDRE0))) {}
        UDR0 = *text++;
    }
}

int main(void) {
    UBRR0H = 0;
    UBRR0L = 103;
    UCSR0A = 0;
    UCSR0B = _BV(TXEN0) | _BV(RXEN0);
    UCSR0C = _BV(UCSZ01) | _BV(UCSZ00);
    print("Console input ready\n");
    char line[128];
    unsigned int length = 0;
    for (;;) {
        while (!(UCSR0A & _BV(RXC0))) {}
        char input = UDR0;
        if (input == '\n') {
            line[length] = '\0';
            print("Received: ");
            print(line);
            print("\n");
            length = 0;
        } else if (length < sizeof(line) - 1) {
            line[length++] = input;
        }
    }
}
