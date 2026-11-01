package com.fares.glass.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * tvOS-style grid on a 960×540 dp screen (1080p at density 2).
 * The number of columns and the icon size come from the Dock & icons settings (see DockSettings), so most of the
 * values below are computed each time they are read. With the default settings (6 apps, medium icons) they are
 * the same as before: 6 columns, tiles of about 126 × 76 dp.
 */
object Metrics {
    /** Apps per row. The first row is the dock. */
    val COLS: Int get() = DockSettings.appsInDock

    /** Space on the left and right of the screen for the greeting, the top buttons and the menus. */
    val Margin = 48.dp

    /** Gap between icons: tighter with large icons, roomier with small ones. */
    val Gap: Dp
        get() = when (DockSettings.iconSize) {
            0 -> 26.dp
            2 -> 14.dp
            else -> 22.dp
        }

    /** Width a row of icons may use (large icons may go a little closer to the edges). */
    private val RowRoom: Dp get() = if (DockSettings.iconSize == 2) 896.dp else 864.dp

    /** Icon width: the chosen size, but never so wide that [COLS] icons no longer fit on the screen. */
    val TileW: Dp
        get() {
            val wanted = when (DockSettings.iconSize) {
                0 -> 100.dp
                2 -> 142.dp
                else -> 126.dp
            }
            val cols = COLS
            val fit = (RowRoom - Gap * (cols - 1)) / cols
            return if (wanted < fit) wanted else fit
        }

    /** 5:3, like tvOS icons. */
    val TileH: Dp get() = TileW * 0.6f

    /** Distance from one row to the next. */
    val Pitch: Dp get() = TileH + 52.dp

    /** Width of a whole row of icons, and where it starts so that the row is centered on the screen. */
    val RowWidth: Dp get() = TileW * COLS + Gap * (COLS - 1)
    val RowStart: Dp get() = (960.dp - RowWidth) / 2

    /** First row position at the top: the Top Shelf fills the space above it. */
    val TopRowY = 392.dp

    /** Position of the selected row once you have moved into the grid. */
    val GridRowY = 150.dp

    /** How much the selected icon grows (Dock & icons > Focus zoom). */
    val FOCUS_SCALE: Float
        get() = when (DockSettings.zoom) {
            0 -> 1f
            2 -> 1.25f
            else -> 1.13f
        }
}

enum class Mode { Browse, Top, Jiggle, Menu, Control }

/** Last requested move: the tile tilts that way (or "bumps" when blocked). */
data class Nudge(val dx: Int, val dy: Int, val tick: Int)

class HomeState {
    var focus by mutableIntStateOf(0)
    var mode by mutableStateOf(Mode.Browse)
    var menuIndex by mutableIntStateOf(0)
    var controlIndex by mutableIntStateOf(0)
    var topIndex by mutableIntStateOf(0)
    var nudge by mutableStateOf(Nudge(0, 0, 0))
    /** OK held on a tile: it presses in before the app opens. */
    var pressed by mutableStateOf(false)

    // Long press on OK (not Compose state, so it triggers no redraw).
    var centerDownAt = -1L
    var longFired = false

    val row get() = focus / Metrics.COLS

    /** Home pressed while already home: back to the top, as on tvOS. */
    fun goHome() {
        mode = Mode.Browse
        focus %= Metrics.COLS
    }

    fun bump(dx: Int, dy: Int) {
        nudge = Nudge(dx, dy, nudge.tick + 1)
    }

    companion object {
        /** Cell a move lands on, or null when it hits an edge. */
        fun target(from: Int, dx: Int, dy: Int, count: Int): Int? {
            val c = Metrics.COLS
            val col = from % c
            val row = from / c
            return when {
                dx < 0 -> if (col > 0) from - 1 else null
                dx > 0 -> if (col < c - 1 && from + 1 < count) from + 1 else null
                dy < 0 -> if (row > 0) from - c else null
                dy > 0 -> when {
                    from + c < count -> from + c
                    row < (count - 1) / c -> count - 1
                    else -> null
                }
                else -> null
            }
        }
    }
}
