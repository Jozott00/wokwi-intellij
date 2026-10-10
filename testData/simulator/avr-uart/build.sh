#!/bin/sh
# Regenerate with Arduino AVR GCC 7.3.0-atmel3.6.1-arduino7; binaries are committed test inputs.
set -eu
cd "$(dirname "$0")"
"${AVR_GCC:-avr-gcc}" -mmcu=atmega328p -DF_CPU=16000000UL -O0 -gdwarf-2 \
    "-fdebug-prefix-map=$PWD=/wokwi-avr-fixture" -Wall -Wextra -o firmware.elf main.c
"${AVR_OBJCOPY:-avr-objcopy}" -O ihex -R .eeprom firmware.elf firmware.hex
# Match the LF bytes pinned by Git attributes and SHA256SUMS.
LC_ALL=C tr -d '\r' < firmware.hex > firmware.hex.tmp
mv firmware.hex.tmp firmware.hex
chmod 644 firmware.elf
shasum -a 256 main.c build.sh README.md wokwi.toml diagram.json firmware.elf firmware.hex > SHA256SUMS
