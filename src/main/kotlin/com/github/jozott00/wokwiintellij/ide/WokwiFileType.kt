package com.github.jozott00.wokwiintellij.ide

import com.github.jozott00.wokwiintellij.ui.WokwiIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFileType

object WokwiFileType : LanguageFileType(TomlLanguage), FileTypeIdentifiableByVirtualFile {

    override fun getName() = "WOKWI_TOML"

    override fun getDescription() = "Wokwi configuration"

    override fun getDefaultExtension() = "toml"

    override fun getIcon() = WokwiIcons.ConfigFile

    override fun isMyFileType(file: VirtualFile): Boolean {
        return file.nameWithoutExtension == "wokwi" && file.extension == TomlFileType.defaultExtension
    }
}