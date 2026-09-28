package top.ntutn.floatclock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okio.Buffer
import top.ntutn.floatclock.storage.ThemeModel
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClockSettingsTest {
    private val original = ThemeModel("digital", 26, 59, 50, backgroundColor = "black")

    @Test
    fun oldConfigurationAndRoundTrip() = runBlocking {
        val oldJson = """{"theme":"normal","colorR":26,"colorG":59,"colorB":50,"backgroundColor":"black"}"""
        val old = ThemeModel.Serializer.readFrom(Buffer().writeUtf8(oldJson))
        assertFalse(old.showOutline)
        assertEquals("white", old.outlineColor)
        assertEquals(Color.Black, old.appearance().background)
        val changed = old.copy(showOutline = true, outlineColor = "black")
        val buffer = Buffer()
        ThemeModel.Serializer.writeTo(changed, buffer)
        assertEquals(changed, ThemeModel.Serializer.readFrom(buffer))
    }

    @Test
    fun outlineOverridesBackgroundWithoutDestroyingPreference() {
        val outlined = original.copy(showOutline = true, outlineColor = "invalid")
        assertEquals(Color.Transparent, outlined.appearance().background)
        assertEquals(Color.White, outlined.appearance().outlineColor)
        assertEquals("black", outlined.backgroundColor)
        assertEquals(Color.Black, outlined.copy(showOutline = false).appearance().background)
    }

    @Test
    fun queuedEditsStayVisibleAndLastChoiceWins() = runBlocking {
        var saved = original
        val firstWrite = CompletableDeferred<Unit>()
        var writes = 0
        val settings = ClockSettings({ saved }) { edit ->
            if (writes++ == 0) firstWrite.await()
            edit(saved).also { saved = it }
        }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { settings.run() }
        try {
            settings.update { it.copy(showOutline = true) }
            yield()
            settings.update { it.copy(outlineColor = "black") }
            settings.update { it.copy(showOutline = false) }
            assertFalse(settings.model.showOutline)
            assertEquals("black", settings.model.outlineColor)
            firstWrite.complete(Unit)
            yield()
            assertEquals(settings.model, saved)
            assertEquals(3, writes)
            assertEquals(Color.Black, saved.appearance().background)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun failedWriteRollsBackWithoutDiscardingLaterEdits() = runBlocking {
        var saved = original
        var writes = 0
        val settings = ClockSettings({ saved }) { edit ->
            if (writes++ == 0) throw IOException("Simulated write failure")
            edit(saved).also { saved = it }
        }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { settings.run() }
        try {
            settings.update { it.copy(showOutline = true) }
            settings.update { it.copy(outlineColor = "black") }
            assertTrue(settings.model.showOutline)
            yield()
            assertFalse(settings.model.showOutline)
            assertEquals("black", settings.model.outlineColor)
            assertEquals(saved, settings.model)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun naturalSizeGrowsUntilConfigurationIsReset() {
        val size = ClockContentSize()
        assertEquals(IntSize(100, 50), size.include(100, 50))
        assertEquals(IntSize(240, 60), size.include(240, 60))
        assertEquals(IntSize(240, 60), size.include(80, 40))
        assertEquals(IntSize(80, 40), ClockContentSize().include(80, 40))
    }
}
