package com.github.jozott00.wokwiintellij.toml

import com.intellij.openapi.vfs.VirtualFile

data class WokwiConfig(
    val version: String,
    val elf: VirtualFile,
    val firmware: VirtualFile,


    )