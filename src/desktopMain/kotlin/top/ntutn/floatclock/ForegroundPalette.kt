package top.ntutn.floatclock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory
import kotlin.random.Random

internal data class ForegroundColor(val name: String, val rgb: Int) {
    val hex: String get() = "#%06X".format(rgb)
}

/** Immutable bundled catalog. Hex is authoritative; the source RGB arrays can disagree. */
internal class ForegroundPalette(val colors: List<ForegroundColor>) {
    private val byRgb = colors.associateBy { it.rgb }

    fun resolve(rgb: Int): ForegroundColor = byRgb[rgb]
        ?: ForegroundColor("自定义色 #%06X".format(rgb), rgb)

    fun initialSelection(rgb: Int, random: Random = Random.Default): ForegroundSelection =
        ForegroundSelection(
            listOf(resolve(rgb)) + colors.filter { it.rgb != rgb }.shuffled(random).take(6),
            selectedIndex = 0,
        )

    fun randomColor(currentRgb: Int, random: Random = Random.Default): ForegroundColor? =
        colors.filter { it.rgb != currentRgb }.randomOrNull(random)

    companion object {
        private val logger = LoggerFactory.getLogger("ForegroundPalette")
        private val hexPattern = Regex("#[0-9a-fA-F]{6}")

        fun load(): ForegroundPalette = loadResource {
            ForegroundPalette::class.java.getResourceAsStream("/colors.json")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Missing colors.json resource")
        }

        internal fun loadResource(read: () -> String): ForegroundPalette = try {
            parse(read())
        } catch (exception: Exception) {
            logger.warn("Failed to load foreground palette; keeping current color", exception)
            ForegroundPalette(emptyList())
        }

        internal fun parse(source: String): ForegroundPalette {
            val entries = Json.parseToJsonElement(source) as? JsonArray
                ?: error("Foreground palette must be a JSON array")
            val colors = entries.mapNotNull { entry ->
                val obj = entry as? JsonObject
                val name = (obj?.get("name") as? JsonPrimitive)?.contentOrNull?.trim()
                val hex = (obj?.get("hex") as? JsonPrimitive)?.contentOrNull
                if (name.isNullOrEmpty() || hex == null || !hexPattern.matches(hex)) null
                else ForegroundColor(name, hex.substring(1).toInt(16))
            }.distinctBy { it.rgb }
            if (colors.size != entries.size) {
                logger.warn("Discarded {} invalid or duplicate foreground colors", entries.size - colors.size)
            }
            if (colors.size < 7) logger.warn("Foreground palette contains only {} usable colors", colors.size)
            return ForegroundPalette(colors)
        }
    }
}

/** A session keeps its slots; a new external color replaces only the selected slot. */
internal data class ForegroundSelection(val colors: List<ForegroundColor>, val selectedIndex: Int) {
    fun select(color: ForegroundColor): ForegroundSelection {
        val existingIndex = colors.indexOfFirst { it.rgb == color.rgb }
        return if (existingIndex >= 0) copy(selectedIndex = existingIndex)
        else copy(colors = colors.toMutableList().also { it[selectedIndex] = color })
    }
}
