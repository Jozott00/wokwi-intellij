package com.github.jozott00.wokwiintellij.runner

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.runners.ExecutionEnvironment

class WokwiSimulatorState(val myEnvironment: ExecutionEnvironment) : CommandLineState(myEnvironment) {
    override fun startProcess() = WokwiProcessHandler(myEnvironment.project)
}