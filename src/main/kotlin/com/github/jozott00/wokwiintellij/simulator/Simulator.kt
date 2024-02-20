package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware
import com.github.jozott00.wokwiintellij.simulator.gdb.GDBServerCommunicator

interface Simulator {

    fun start()
    
    fun setFirmware(firmware: WokwiArgsFirmware)

    fun getFirmware(): WokwiArgsFirmware

    suspend fun connectToGDBServer(server: GDBServerCommunicator)

}