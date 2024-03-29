package com.github.jozott00.wokwiintellij.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer

fun Disposable.disposeByDisposer() {
    invokeLater {
        Disposer.dispose(this)
    }
}