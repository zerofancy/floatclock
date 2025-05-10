package top.ntutn.floatclock.storage

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import java.awt.Color

class ThemeDataStore(private val produceFilePath: () -> String) {
    private val db = DataStoreFactory.create(storage = OkioStorage<ThemeModel>(
        fileSystem = FileSystem.SYSTEM,
        serializer = ThemeModel.Serializer,
        producePath = {
            produceFilePath().toPath()
        }
    ))

    fun themeData() = db.data

    suspend fun updateColor(color: Color) = db.updateData { theme ->
        theme.copy(colorR = color.red, colorG = color.green, colorB = color.blue)
    }

    suspend fun updateTheme() = db.updateData {
        val newTheme = if (it.theme == "digital") {
            "normal"
        } else {
            "digital"
        }
        it.copy(theme = newTheme)
    }
}