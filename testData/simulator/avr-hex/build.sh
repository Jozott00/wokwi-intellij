#!/bin/sh
# Regenerate the checked-in artifacts with Arduino AVR GCC 7.3.0-atmel3.6.1-arduino7.
set -eu
cd "$(dirname "$0")"
"${AVR_GCC:-avr-gcc}" -mmcu=atmega328p -DF_CPU=16000000UL -Os -g -Wall -Wextra -o firmware.elf main.c
"${AVR_OBJCOPY:-avr-objcopy}" -O ihex -R .eeprom firmware.elf firmware.hex
# Match the LF bytes pinned by Git attributes and SHA256SUMS.
LC_ALL=C tr -d '\r' < firmware.hex > firmware.hex.tmp
mv firmware.hex.tmp firmware.hex
# ELF is test data, not a host executable.
chmod 644 firmware.elf
