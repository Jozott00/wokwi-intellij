package com.github.jozott00.wokwiintellij.utils

import org.intellij.lang.annotations.Language

object WokwiTemplates {

    fun defaultDiagramJson(): String {
        @Language("JSON")
        val diagram = """
            {
                "version": 1,
                "editor": "wokwi",
                "author": "Cool Dude",
                "parts": [{
                    "type": "board-esp32-s3-devkitc-1",
                    "id": "esp",
                    "top": 0.59,
                    "left": 0.67,
                    "attrs": {
                        "flashSize": "16"
                    }
                }],
                "connections": [ [ "esp:TX", "${'$'}serialMonitor:RX", "", [] ], [ "esp:RX", "${'$'}serialMonitor:TX", "", [] ] ]
            }
        """.trimIndent()

        return diagram
    }
}