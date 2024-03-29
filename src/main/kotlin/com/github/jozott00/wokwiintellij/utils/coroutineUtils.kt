package com.github.jozott00.wokwiintellij.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume

@Suppress("unused")
suspend inline fun <T : Closeable?, R> T.useCancellably(
    crossinline block: (T) -> R
): R = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { this?.close() }
    cont.resume(use(block))
}

suspend inline fun <T : Closeable?, R> T.runCloseable(
    crossinline block: (T) -> R
): R = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { this?.close() }
    cont.resume(block(this))
}
