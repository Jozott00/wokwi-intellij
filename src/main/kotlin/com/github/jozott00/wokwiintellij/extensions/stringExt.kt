package com.github.jozott00.wokwiintellij.extensions

fun String.hexStringToByteArray(): ByteArray? = this.removePrefix("0x")
    .chunked(2)
    .map { it.toIntOrNull(16) ?: return null }
    .map { it.toByte() }
    .toByteArray()
