package com.fares.glass.ui

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The TV's video inputs (HDMI 1, HDMI 2, AV...). */
object InputSources {

    class Source(val id: String, val label: String)

    fun list(context: Context): List<Source> {
        val tv = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return try {
            tv.tvInputList
                .filter { it.isPassthroughInput }
                .map { Source(it.id, it.loadLabel(context)?.toString() ?: it.id) }
        } catch (e: Exception) {
            Log.w("Glass", "Can't list the TV inputs", e)
            emptyList()
        }
    }

    /** Switches the TV to this input. Returns false if the TV would not do it. */
    fun open(context: Context, source: Source): Boolean = try {
        val uri = TvContract.buildChannelUriForPassthroughInput(source.id)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w("Glass", "Can't open the input ${source.label}", e)
        false
    }
}

/** Small list of inputs under the top buttons. selected = the highlighted row. */
@Composable
fun InputPicker(backdrop: Backdrop, labels: List<String>, selected: Int, modifier: Modifier = Modifier) {
    Box(modifier.width(220.dp)) {
        Glass(backdrop, Modifier.matchParentSize(), shape = RoundedCornerShape(24.dp), tint = Color.White.copy(alpha = 0.10f))
        Column(Modifier.padding(10.dp)) {
            if (labels.isEmpty()) {
                BasicText(
                    "No inputs found",
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = txt(Color.White.copy(alpha = 0.8f), 14.sp, FontWeight.Medium),
                )
            }
            labels.forEachIndexed { i, label ->
                val on = i == selected
                BasicText(
                    label,
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(if (on) Color.White.copy(alpha = 0.95f) else Color.Transparent, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    style = txt(if (on) Color(0xFF111114) else Color.White, 14.sp, FontWeight.SemiBold),
                    maxLines = 1,
                )
            }
        }
    }
}
