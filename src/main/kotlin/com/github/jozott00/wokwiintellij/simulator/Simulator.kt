package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware

interface Simulator {

    fun start()

    fun setFirmware(firmware: WokwiArgsFirmware)

    fun getFirmware(): WokwiArgsFirmware

}