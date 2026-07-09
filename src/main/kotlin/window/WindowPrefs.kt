package window

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.util.prefs.Preferences

private const val DEFAULT_WIDTH = 1400
private const val DEFAULT_HEIGHT = 900
private const val MIN_SIZE = 480

object WindowPrefs {
    private val prefs: Preferences = Preferences.userRoot().node("imgdiff-desktop/window")

    fun load(): DpSize {
        val w = prefs.getInt("width", DEFAULT_WIDTH).coerceAtLeast(MIN_SIZE)
        val h = prefs.getInt("height", DEFAULT_HEIGHT).coerceAtLeast(MIN_SIZE)
        return DpSize(w.dp, h.dp)
    }

    fun save(size: DpSize) {
        val w = size.width.value.toInt()
        val h = size.height.value.toInt()
        if (w < MIN_SIZE || h < MIN_SIZE) return
        prefs.putInt("width", w)
        prefs.putInt("height", h)
    }
}

