package com.github.jozott00.wokwiintellij.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer
import java.io.Closeable

fun Disposable.disposeByDisposer() {
    invokeLater {
        Disposer.dispose(this)
    }
}

class DisposableRef<T : Closeable>(val value: T) : Disposable {
    private var disposed = false

    override fun dispose() {
        if (disposed) return

        disposed = true
        value.close()
    }
}

fun <T : Closeable> T.asDisposableRef() = DisposableRef(this)
