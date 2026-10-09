package top.ntutn.floatclock

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import top.ntutn.floatclock.storage.ThemeModel
import java.io.IOException
import javax.swing.JRadioButtonMenuItem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForegroundPaletteTest {
    private val palette = ForegroundPalette((1..20).map { ForegroundColor("色$it", it) })

    @Test
    fun bundledCatalogUsesHexEvenWhenRgbDisagrees() {
        val loaded = ForegroundPalette.load()
        assertEquals(526, loaded.colors.size)
        assertEquals(526, loaded.colors.map { it.rgb }.toSet().size)
        assertEquals("杏仁黄", loaded.resolve(0xf7e8aa).name)
        assertEquals("深海绿", loaded.resolve(0x1a3b32).name)
    }

    @Test
    fun invalidEntriesAndDuplicateRgbAreDiscarded() {
        val parsed = ForegroundPalette.parse("""[
            {"name":"first","hex":"#abcdef","RGB":[0,0,0]},
            {"name":"duplicate","hex":"#ABCDEF"},
            {"name":"","hex":"#112233"},
            {"name":"bad","hex":"red"}, null, {}
        ]""")
        assertEquals(listOf(ForegroundColor("first", 0xabcdef)), parsed.colors)
        assertTrue(ForegroundPalette.loadResource { "broken" }.colors.isEmpty())
        assertTrue(ForegroundPalette.loadResource { throw IOException("missing") }.colors.isEmpty())
    }

    @Test
    fun startupPreservesKnownAndLegacyColorsWithoutDuplicates() {
        for (rgb in listOf(5, 0xabcdef)) {
            val selection = palette.initialSelection(rgb, Random(42))
            assertEquals(7, selection.colors.size)
            assertEquals(7, selection.colors.map { it.rgb }.toSet().size)
            assertEquals(rgb, selection.colors[selection.selectedIndex].rgb)
            assertTrue(selection.colors.drop(1).all { it in palette.colors })
        }
        assertEquals("自定义色 #ABCDEF", palette.resolve(0xabcdef).name)
        assertEquals(7, palette.initialSelection(5, Random(42)).colors.size)
    }

    @Test
    fun selectingExistingColorKeepsSlotsAndExternalColorReplacesSelectedSlot() {
        val initial = palette.initialSelection(1, Random(42))
        val existing = initial.select(initial.colors[3])
        assertEquals(initial.colors, existing.colors)
        assertEquals(3, existing.selectedIndex)
        val external = palette.colors.first { it !in initial.colors }
        val replaced = existing.select(external)
        assertEquals(3, replaced.selectedIndex)
        assertEquals(external, replaced.colors[3])
        assertEquals(initial.colors.filterIndexed { i, _ -> i != 3 }, replaced.colors.filterIndexed { i, _ -> i != 3 })
        assertEquals(7, replaced.colors.map { it.rgb }.toSet().size)
        val rollback = replaced.select(initial.colors[3])
        assertEquals(initial.colors, rollback.colors)
    }

    @Test
    fun randomExcludesCurrentAndDegradesWithoutInventingColors() {
        repeat(100) { seed ->
            val next = palette.randomColor(5, Random(seed))!!
            assertTrue(next in palette.colors)
            assertTrue(next.rgb != 5)
        }
        val empty = ForegroundPalette(emptyList())
        assertEquals(listOf(empty.resolve(5)), empty.initialSelection(5).colors)
        assertNull(empty.randomColor(5))
        val single = ForegroundPalette(listOf(ForegroundColor("only", 5)))
        assertNull(single.randomColor(5))
        assertEquals(2, single.initialSelection(6).colors.size)
        assertEquals(5, single.randomColor(6)!!.rgb)
    }

    @Test
    fun swingMenuWaitsForSettingsAndTracksFailureAndQueuedEdits() = runBlocking(Dispatchers.Swing) {
        var saved = ThemeModel("digital", 0, 0, 1)
        var failWrite = true
        val settings = ClockSettings({ saved }) { edit ->
            if (failWrite) throw IOException("Simulated palette save failure")
            edit(saved).also { saved = it }
        }
        val menu = ForegroundColorMenu(palette, settings)
        menu.refresh()
        menu.applyRandomColor()
        assertFalse(menu.menu.isEnabled)
        assertEquals(0, menu.menu.itemCount)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { settings.run() }
        try {
            menu.refresh()
            val items = (0 until 7).map { menu.menu.getItem(it) as JRadioButtonMenuItem }
            val originalLabels = items.map { it.text }
            menu.refresh()
            assertEquals(originalLabels, items.map { it.text })
            items[3].doClick(0)
            assertTrue(items[3].isSelected)
            yield()
            menu.refresh()
            assertEquals(1, settings.model.foregroundRgb())
            assertTrue(items[0].isSelected)
            assertEquals(1, items.count { it.isSelected })

            // Exercise replacement and stale listener protection through a settings change.
            settings.update { it.copy(colorB = 100) }
            menu.refresh()
            assertEquals("自定义色 #000064", items[0].text)
            failWrite = false
            items[2].doClick(0)
            items[0].doClick(0)
            yield()
            menu.refresh()
            assertEquals(100, saved.foregroundRgb())
            assertEquals(saved, settings.model)
            assertTrue(items[0].isSelected)
            assertEquals(1, items.count { it.isSelected })
            menu.applyRandomColor()
            assertTrue(settings.model.foregroundRgb() in 1..20)
            assertEquals(1, items.count { it.isSelected })
        } finally {
            job.cancelAndJoin()
        }
    }
}
