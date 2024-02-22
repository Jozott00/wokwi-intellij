package com.github.jozott00.wokwiintellij.exceptions

open class WokwiError
data class GenericError(val title: String, val message: String): WokwiError()