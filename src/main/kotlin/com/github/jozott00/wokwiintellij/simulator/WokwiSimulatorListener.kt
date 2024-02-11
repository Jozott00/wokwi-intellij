package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.intellij.openapi.util.Key

interface WokwiSimulatorListener {
    fun onStarted(runArgs: WokwiArgs) {}
    fun onShutdown() {}
    fun onTextAvailable(text: String, outputType: Key<*>) {}
}