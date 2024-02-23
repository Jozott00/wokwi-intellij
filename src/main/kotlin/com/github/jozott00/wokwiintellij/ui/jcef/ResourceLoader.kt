package com.github.jozott00.wokwiintellij.ui.jcef

object ResourceLoader {
    class Resource(
        val content: ByteArray,
        val type: String? = null
    )

    fun <T> loadInternalResource(cls: Class<T>, path: String, contentType: String?): Resource? {
        return cls.getResourceAsStream(path)?.use {
            Resource(it.readBytes(), contentType)
        }
    }
}