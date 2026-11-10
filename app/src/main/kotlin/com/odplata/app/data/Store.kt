package com.odplata.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** All user data lives in one JSON file on the phone. Writes go through a temp file so a crash never leaves it half-written. */
class Store(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppData = try {
        if (file.exists()) json.decodeFromString<AppData>(file.readText()) else AppData()
    } catch (e: Exception) {
        // Keep the unreadable file aside instead of silently overwriting the user's history.
        runCatching { file.copyTo(File(file.parentFile, "data.broken.json"), overwrite = true) }
        AppData()
    }

    fun save(data: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(data))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        fun of(context: Context) = Store(File(context.filesDir, "data.json"))
    }
}

object Catalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<ExerciseDef> =
        context.assets.open("exercises.json").use { json.decodeFromString(it.readBytes().decodeToString()) }
}
