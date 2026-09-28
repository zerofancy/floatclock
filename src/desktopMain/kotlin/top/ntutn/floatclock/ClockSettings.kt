package top.ntutn.floatclock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import top.ntutn.floatclock.storage.ThemeDataStore
import top.ntutn.floatclock.storage.ThemeModel

/** Owned by the UI thread. Replays pending edits after each serialized write or failure. */
internal class ClockSettings(
    private val load: suspend () -> ThemeModel,
    private val save: suspend ((ThemeModel) -> ThemeModel) -> ThemeModel,
) {
    constructor(store: ThemeDataStore) : this({ store.themeData().first() }, { store.update(it) })
    private val logger = LoggerFactory.getLogger("ClockSettings")
    private val commands = Channel<(ThemeModel) -> ThemeModel>(Channel.UNLIMITED)
    private val pending = mutableListOf<(ThemeModel) -> ThemeModel>()
    var model by mutableStateOf(ThemeModel.Serializer.defaultValue)
        private set
    var ready by mutableStateOf(false)
        private set

    fun update(transform: (ThemeModel) -> ThemeModel) {
        if (!ready) return
        pending.add(transform)
        model = transform(model)
        check(commands.trySend(transform).isSuccess)
    }

    suspend fun run() {
        var confirmed = load()
        model = confirmed
        ready = true
        for (transform in commands) {
            try {
                confirmed = save(transform)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("Failed to save clock appearance", exception)
            }
            pending.removeAt(0)
            model = pending.fold(confirmed) { value, edit -> edit(value) }
        }
    }
}
