package no.esotericgames.quotes.server

import java.io.File

fun loadDotEnvIntoSystemProperties(path: String = ".env") {
    val file = File(path)
    if (!file.isFile) return

    file.forEachLine { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine

        val separatorIndex = line.indexOf('=')
        if (separatorIndex < 0) return@forEachLine

        val key = line.substring(0, separatorIndex).trim()
        val value = line.substring(separatorIndex + 1).trim().removeSurrounding("\"").removeSurrounding("'")

        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value)
        }
    }
}
