package com.github.jozott00.wokwiintellij.simulator

import com.intellij.openapi.vfs.VirtualFile

class WokwiConfig(
    val version: String,
    val elf: VirtualFile,
    val firmware: VirtualFile,
    val diagram: VirtualFile,
)