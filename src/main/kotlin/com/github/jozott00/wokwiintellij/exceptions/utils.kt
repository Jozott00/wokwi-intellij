package com.github.jozott00.wokwiintellij.exceptions


inline fun <R> catchIllArg(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }
}