# AVR console input fixture

An Arduino Uno / ATmega328P at 16 MHz uses UART0 at 9600 baud with transmit and
receive enabled. After `Console input ready` followed by LF, it collects bytes
until LF and replies with `Received: `, the received line, and LF. The reply
prefix distinguishes firmware output from the IDE console's local input echo.
Lines can contain up to 127 bytes; UTF-8 bytes are returned unchanged.

`ConsoleInputTest` submits ASCII, UTF-8 and empty lines through the actual Run
console editor and checks the firmware response to each line.

## Regeneration

Use the same Arduino AVR GCC `7.3.0-atmel3.6.1-arduino7` toolchain documented in
the [AVR UART fixture](../avr-uart/README.md):

```sh
AVR_GCC=/path/to/avr/bin/avr-gcc AVR_OBJCOPY=/path/to/avr/bin/avr-objcopy sh build.sh
```

This updates the committed HEX/ELF and `SHA256SUMS`. Tests verify the manifest
and use those artifacts without requiring a compiler.
